import type { Role } from '../types/auth.ts'

// 백엔드 RoleHierarchy: SUPER_ADMIN > ADMIN > USER. hasRole('ADMIN') API 는 SUPER_ADMIN 도 통과한다.
// role === 'ADMIN' 처럼 직접 비교하면 SUPER_ADMIN 이 빠지므로 화면에서는 이 함수를 쓴다. (실제 방어는 백엔드 403)

export function isAdmin(role: Role): boolean {
  return role === 'ADMIN' || role === 'SUPER_ADMIN'
}

export function isSuperAdmin(role: Role): boolean {
  return role === 'SUPER_ADMIN'
}
