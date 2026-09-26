import type { ReactNode } from 'react'
import { Link } from 'react-router'

interface BackLinkProps {
  to: string
  children: ReactNode
}

export default function BackLink({ to, children }: BackLinkProps) {
  return (
    <Link to={to} className="text-sm text-indigo-600 hover:underline">
      {children}
    </Link>
  )
}
