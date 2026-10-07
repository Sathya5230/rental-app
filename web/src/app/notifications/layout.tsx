import { RequireSignIn } from '@/components/RequireSignIn';

export default function NotificationsLayout({ children }: { children: React.ReactNode }) {
  return <RequireSignIn>{children}</RequireSignIn>;
}
