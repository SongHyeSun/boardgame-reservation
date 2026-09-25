import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <section className="py-8 text-center">
      <h1 className="text-2xl font-bold">페이지를 찾을 수 없습니다</h1>
      <p className="mt-2 text-gray-500">주소가 잘못되었거나 삭제된 페이지입니다.</p>
      <Link to="/" className="mt-4 inline-block text-indigo-600 hover:underline">
        홈으로
      </Link>
    </section>
  )
}
