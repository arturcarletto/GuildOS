import { Route, Routes } from 'react-router-dom';

import AppShell from './components/AppShell';
import DashboardPage from './pages/DashboardPage';
import GuildDetailPage from './pages/GuildDetailPage';
import GuildsPage from './pages/GuildsPage';
import LandingPage from './pages/LandingPage';
import NotFoundPage from './pages/NotFoundPage';

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<LandingPage />} />
      <Route path="/dashboard" element={<AppShell />}>
        <Route index element={<DashboardPage />} />
        <Route path="guilds" element={<GuildsPage />} />
        <Route path="guilds/:discordGuildId" element={<GuildDetailPage />} />
      </Route>
      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  );
}
