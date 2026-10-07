import type { Metadata } from 'next';
import { Inter, Plus_Jakarta_Sans } from 'next/font/google';
import { AppProviders } from './providers';
import './globals.css';

const inter = Inter({ subsets: ['latin'], variable: '--font-inter' });
const jakarta = Plus_Jakarta_Sans({ subsets: ['latin'], weight: ['500', '600', '700', '800'], variable: '--font-jakarta' });

export const metadata: Metadata = {
  title: 'RentNest',
  description: 'Rent cameras, tools, camping gear and more by the day or week.',
};

export default function RootLayout({ children }: LayoutProps<'/'>) {
  return (
    <html lang="en" data-mode="customer" className={`${inter.variable} ${jakarta.variable}`} suppressHydrationWarning>
      <body className="min-h-dvh bg-background font-sans text-on-surface antialiased">
        <AppProviders>{children}</AppProviders>
      </body>
    </html>
  );
}
