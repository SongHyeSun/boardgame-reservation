import { Link } from 'react-router'

export default function NotFoundPage() {
  return (
    <section className="py-12 text-center">
      <p className="text-5xl font-bold text-indigo-600">404</p>
      <h1 className="mt-4 text-2xl font-bold">페이지를 찾을 수 없습니다</h1>
      <p className="mt-2 text-gray-500">주소가 잘못되었거나 삭제된 페이지입니다.</p>
      <div className="mt-6 flex justify-center gap-2">
        <Link to="/parties" className="rounded bg-indigo-600 px-4 py-2 font-medium text-white hover:bg-indigo-700">
          파티 목록
        </Link>
        <Link to="/boardgames" className="rounded border border-gray-300 bg-white px-4 py-2 text-gray-700 hover:bg-gray-50">
          게임 목록
        </Link>
      </div>
    </section>
  )
}
