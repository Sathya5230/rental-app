import { RequireSignIn } from '@/components/RequireSignIn';

export default function CustomerLayout({ children }: { children: React.ReactNode }) {
  return <RequireSignIn>{children}</RequireSignIn>;
}
