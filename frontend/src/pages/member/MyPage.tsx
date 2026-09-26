import { useMe } from '../../hooks/useMe.ts'
import AdminRequestSection from './AdminRequestSection.tsx'
import PasswordSection from './PasswordSection.tsx'
import ProfileSection from './ProfileSection.tsx'

export default function MyPage() {
  const { data: me } = useMe()

  // ProtectedRoute 아래라 보통 로그인 상태다. 로그아웃 직후 캐시가 null 이 된 한 번의 렌더는 비워 둔다.
  if (!me) {
    return null
  }
  return (
    <section className="mx-auto max-w-2xl space-y-6">
      <h1 className="text-2xl font-bold">내 정보</h1>
      <ProfileSection me={me} />
      <AdminRequestSection me={me} />
      <PasswordSection />
    </section>
  )
}
