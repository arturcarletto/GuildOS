import { Link } from 'react-router-dom';

export default function NotFoundPage() {
  return (
    <div className="landing">
      <div className="landing__inner">
        <section className="hero">
          <span className="hero__badge">404</span>
          <h1 className="hero__title">This page wandered off</h1>
          <p className="hero__lead">
            The page you were looking for doesn’t exist or has moved.
          </p>
          <Link className="btn btn--primary" to="/dashboard">
            Back to dashboard
          </Link>
        </section>
      </div>
    </div>
  );
}
