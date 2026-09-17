import axios from 'axios'

const API_BASE = '/api'

export async function fetchQualityMetrics(): Promise<any[]> {
  try {
    const response = await axios.get(`${API_BASE}/measures/component`, {
      params: {
        component: window.location.pathname.split('/')[2], // project key from URL
        metricKeys: [
          'reliability_rating',
          'security_rating',
          'sqale_rating',
          'coverage',
          'duplicated_lines_density',
          'mutation_score',
          'blocker_violations',
          'critical_violations',
          'major_violations',
          'minor_violations',
          'info_violations',
        ].join(',')
      }
    })
    return response.data.component?.measures || []
  } catch (error) {
    console.error('Failed to fetch quality metrics:', error)
    return []
  }
}

export async function fetchQualityHistory(): Promise<any[]> {
  try {
    const response = await axios.get(`${API_BASE}/measures/search_history`, {
      params: {
        component: window.location.pathname.split('/')[2],
        metrics: 'coverage,mutation_score',
        from: new Date(Date.now() - 30 * 24 * 60 * 60 * 1000).toISOString().split('T')[0],
      }
    })
    return response.data.measures || []
  } catch (error) {
    console.error('Failed to fetch quality history:', error)
    return []
  }
}

export async function fetchMutationReport(): Promise<any> {
  try {
    const projectKey = window.location.pathname.split('/')[2]
    const branch = window.location.pathname.split('/')[4] || 'main'
    const response = await axios.get(`${API_BASE}/chillcode_mutation/report`, {
      params: { projectKey, branch }
    })
    return response.data
  } catch (error) {
    console.error('Failed to fetch mutation report:', error)
    return null
  }
}

export async function fetchMutationSummary(): Promise<any> {
  try {
    const projectKey = window.location.pathname.split('/')[2]
    const branch = window.location.pathname.split('/')[4] || 'main'
    const response = await axios.get(`${API_BASE}/chillcode_mutation/summary`, {
      params: { projectKey, branch }
    })
    return response.data
  } catch (error) {
    console.error('Failed to fetch mutation summary:', error)
    return null
  }
}