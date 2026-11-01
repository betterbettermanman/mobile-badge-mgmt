let onUnauthorized = () => {}

export function setUnauthorizedHandler(handler) {
  onUnauthorized = handler
}

export async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) }
  let body = options.body
  if (body && !(body instanceof FormData) && typeof body !== 'string') {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(body)
  }
  let response
  try {
    response = await fetch(path, {
      credentials: 'include',
      method: options.method || 'GET',
      headers,
      body
    })
  } catch {
    const error = new Error('网络异常，请稍后重试')
    error.status = 0
    throw error
  }
  const text = await response.text()
  let data = null
  if (text) {
    try {
      data = JSON.parse(text)
    } catch {
      data = text
    }
  }
  if (!response.ok) {
    if (response.status === 401 && !options.allow401) {
      onUnauthorized()
    }
    const error = new Error(data?.message || '网络异常，请稍后重试')
    error.status = response.status
    error.code = data?.code
    throw error
  }
  return data
}
