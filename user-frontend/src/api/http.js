import axios from 'axios'
import { installResponseHandling } from '../../../frontend-shared/apiErrors.js'

const environment = import.meta.env || {}
const http = installResponseHandling(axios.create({
  baseURL: (environment.VITE_API_BASE_URL || '').replace(/\/$/, ''),
  timeout: 20000
}))

export { ApiError, getApiErrorMessage } from '../../../frontend-shared/apiErrors.js'
export default http
