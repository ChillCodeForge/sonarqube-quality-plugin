import React from 'react';
import type { StatusKey } from '../mutants';

export type IconName = StatusKey | 'search' | 'chevron' | 'retry' | 'alert' | 'file';

const CIRCLE = <circle cx="8" cy="8" r="6.25" />;

// One 16px outline set, drawn at a 1.5px stroke so every icon reads at the
// same weight as SonarQube's own.
const SHAPES: Record<IconName, React.ReactNode> = {
  killed: (
    <>
      {CIRCLE}
      <path d="M5.3 8.3l1.9 1.9 3.6-4.1" />
    </>
  ),
  survived: (
    <>
      {CIRCLE}
      <path d="M8 4.7v3.9" />
      <path d="M8 11.1v.1" />
    </>
  ),
  timeout: (
    <>
      {CIRCLE}
      <path d="M8 4.6V8l2.3 1.5" />
    </>
  ),
  noCoverage: <circle cx="8" cy="8" r="6.25" strokeDasharray="2.4 2.5" />,
  ignored: (
    <>
      {CIRCLE}
      <path d="M5.2 8h5.6" />
    </>
  ),
  unknown: (
    <>
      {CIRCLE}
      <path d="M6.3 6.4a1.8 1.8 0 1 1 2.6 1.6c-.6.3-.9.7-.9 1.3" />
      <path d="M8 11.4v.1" />
    </>
  ),
  search: (
    <>
      <circle cx="7" cy="7" r="4.25" />
      <path d="M10.2 10.2l3.3 3.3" />
    </>
  ),
  chevron: <path d="M4 6l4 4 4-4" />,
  retry: (
    <>
      <path d="M13 8a5 5 0 1 1-1.6-3.7" />
      <path d="M13 2.8v2.6h-2.6" />
    </>
  ),
  alert: (
    <>
      <path d="M8 2.2l6 10.6H2z" />
      <path d="M8 6.4v3" />
      <path d="M8 11.2v.1" />
    </>
  ),
  file: (
    <>
      <path d="M4 2h5l3 3v9H4z" />
      <path d="M9 2v3h3" />
    </>
  ),
};

interface IconProps {
  name: IconName;
  status?: StatusKey;
  title?: string;
}

const Icon: React.FC<IconProps> = ({ name, status, title }) => (
  <svg
    className="cq-icon"
    data-status={status}
    viewBox="0 0 16 16"
    role={title ? 'img' : undefined}
    aria-label={title}
    aria-hidden={title ? undefined : true}
  >
    {title && <title>{title}</title>}
    {SHAPES[name]}
  </svg>
);

export default Icon;
