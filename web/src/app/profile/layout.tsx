import { RequireSignIn } from '@/components/RequireSignIn';

export default function ProfileLayout({ children }: { children: React.ReactNode }) {
  return <RequireSignIn>{children}</RequireSignIn>;
}
