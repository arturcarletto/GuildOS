import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter } from 'react-router-dom';

/** Renders a component inside a MemoryRouter so `Link`/`useNavigate` work in tests. */
export function renderWithRouter(
  ui: ReactElement,
  { route = '/', ...options }: { route?: string } & Omit<RenderOptions, 'wrapper'> = {},
) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <MemoryRouter
        initialEntries={[route]}
      >
        {children}
      </MemoryRouter>
    );
  }
  return render(ui, { wrapper: Wrapper, ...options });
}
