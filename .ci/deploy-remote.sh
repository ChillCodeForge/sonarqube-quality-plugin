#!/bin/sh
set -eu

expected_sha="$1"
plugin_dir=/mnt/docker/volume/sonarqube/plugins
target="${plugin_dir}/sonarqube-quality-plugin-1.0.0-SNAPSHOT.jar"
backup="${target}.previous"
candidate=/home/omarchy/.cache/sonarqube-plugin-deploy/candidate.jar
succeeded=false
mutation_started=false
target_existed=false

running_tasks() {
  docker service ps --filter desired-state=running --format '{{.ID}}|{{.CurrentState}}' sonarqube_sonarqube-app \
    | awk -F'|' '$2 ~ /^Running/ {print $1}'
}

service_has_upload_secret() {
  sudo -n docker service inspect --format '{{range .Spec.TaskTemplate.ContainerSpec.Secrets}}{{println .SecretName}}{{end}}' sonarqube_sonarqube-app \
    | grep -Fxq sonarqube_mutation_upload_token
}

service_has_legacy_upload_token() {
  sudo -n docker service inspect --format '{{range .Spec.TaskTemplate.ContainerSpec.Env}}{{println .}}{{end}}' sonarqube_sonarqube-app \
    | grep -Fq 'CHILLCODE_MUTATION_UPLOAD_TOKEN='
}

has_prior_running_task() {
  current_tasks="$2"
  for task in $1; do
    [ -n "$task" ] || continue
    if printf '%s\n' "$current_tasks" | grep -Fqx "$task"; then
      return 0
    fi
  done
  return 1
}

force_rollout() {
  if service_has_legacy_upload_token; then
    prior_tasks=$(running_tasks)
    sudo -n docker service update --env-rm CHILLCODE_MUTATION_UPLOAD_TOKEN sonarqube_sonarqube-app >/dev/null
    wait_for_rollout "$prior_tasks"
  fi
  prior_tasks=$(running_tasks)
  if service_has_upload_secret; then
    sudo -n docker service update --force sonarqube_sonarqube-app >/dev/null
  else
    sudo -n docker service update \
      --secret-add source=sonarqube_mutation_upload_token,target=chillcode_mutation_upload_token,mode=0444 \
      --force sonarqube_sonarqube-app >/dev/null
  fi
  wait_for_rollout "$prior_tasks"
}

wait_for_rollout() {
  prior_tasks="$1"
  deadline=$(($(date +%s) + 300))
  sleep 3
  while :; do
    current_tasks=$(running_tasks)
    update_state=$(docker service inspect --format '{{if .UpdateStatus}}{{.UpdateStatus.State}}{{end}}' sonarqube_sonarqube-app)
    case "$update_state" in
      rollback_*)
        return 1
        ;;
      completed)
        if [ -n "$current_tasks" ] && ! has_prior_running_task "$prior_tasks" "$current_tasks"; then
          if curl -fsS https://sonar.chillcode.de/api/system/status 2>/dev/null | grep -Fq '"status":"UP"'; then
            return 0
          fi
        fi
        ;;
    esac
    [ $(date +%s) -lt $deadline ] || return 1
    sleep 5
  done
}

restore() {
  trap - EXIT HUP INT TERM
  [ "$succeeded" = true ] && return
  [ "$mutation_started" = true ] || return
  if [ "$target_existed" = true ]; then
    sudo -n install -o ansible -g ansible -m 0644 "$backup" "$target"
    force_rollout
    test "$(sudo -n sha256sum "$target" | awk '{print $1}')" = "$previous_sha"
  else
    sudo -n rm -f "$target"
    force_rollout
  fi
}

test "$(sha256sum "$candidate" | awk '{print $1}')" = "$expected_sha"
if sudo -n test -f "$target"; then
  target_existed=true
  sudo -n cp "$target" "$backup"
  previous_sha=$(sudo -n sha256sum "$backup" | awk '{print $1}')
else
  previous_sha=''
fi

trap restore EXIT HUP INT TERM
mutation_started=true
sudo -n install -o ansible -g ansible -m 0644 "$candidate" "$target"
rm -f "$candidate"
force_rollout
test "$(sudo -n sha256sum "$target" | awk '{print $1}')" = "$expected_sha"
succeeded=true
sudo -n rm -f "$backup"
