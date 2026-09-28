#!/bin/sh
set -eu

artifact="backend/target/sonarqube-quality-plugin-1.0.0-SNAPSHOT.jar"
test -f "${artifact}"

apk add --no-cache openssh-client >/dev/null
mkdir -p ~/.ssh
echo "${DEPLOY_KEY_B64}" | base64 -d > ~/.ssh/id_ed25519
chmod 600 ~/.ssh/id_ed25519
install -m 600 .ci/infra-swarm-known_hosts ~/.ssh/known_hosts

artifact_sha=$(sha256sum "${artifact}" | awk '{print $1}')
test -n "${MUTATION_UPLOAD_TOKEN}"

deploy_host="omarchy@192.168.178.30"
stage_dir="/home/omarchy/.cache/sonarqube-plugin-deploy"
candidate="${stage_dir}/candidate.jar"

if ! ssh -o StrictHostKeyChecking=yes -i ~/.ssh/id_ed25519 "${deploy_host}" \
  'sudo -n docker secret inspect sonarqube_mutation_upload_token >/dev/null 2>&1'; then
  printf '%s' "${MUTATION_UPLOAD_TOKEN}" \
    | ssh -o StrictHostKeyChecking=yes -i ~/.ssh/id_ed25519 "${deploy_host}" \
      'sudo -n docker secret create sonarqube_mutation_upload_token - >/dev/null'
fi

ssh -o StrictHostKeyChecking=yes -i ~/.ssh/id_ed25519 "${deploy_host}" \
  "install -d -m 700 '${stage_dir}' && rm -f '${candidate}'"

scp -o StrictHostKeyChecking=yes -i ~/.ssh/id_ed25519 "${artifact}" "${deploy_host}:${candidate}"

ssh -o StrictHostKeyChecking=yes -i ~/.ssh/id_ed25519 "${deploy_host}" \
  "sh -s -- ${artifact_sha}" < .ci/deploy-remote.sh
