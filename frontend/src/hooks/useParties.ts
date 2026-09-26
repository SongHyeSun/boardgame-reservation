import { useQuery } from '@tanstack/react-query'
import { getParties } from '../api/parties.ts'
import type { PartyFilter } from '../types/party.ts'

export function useParties(filter: PartyFilter = {}) {
  return useQuery({
    queryKey: ['parties', filter],
    queryFn: () => getParties(filter),
  })
}
