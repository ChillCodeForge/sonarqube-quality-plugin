import React from 'react';
import Icon from './Icon';
import MutantBar from './MutantBar';
import { FILE_SORTS, formatCount, formatPercent, type FileSort, type FileView } from '../mutants';

interface FileListProps {
  files: FileView[];
  selectedPath: string | null;
  filter: string;
  sort: FileSort;
  onSelect: (path: string) => void;
  onFilter: (filter: string) => void;
  onSort: (sort: FileSort) => void;
}

const FileList: React.FC<FileListProps> = ({
  files,
  selectedPath,
  filter,
  sort,
  onSelect,
  onFilter,
  onSort,
}) => (
  <aside aria-label="Files">
    <div className="cq-filetools">
      <span className="cq-field">
        <Icon name="search" />
        <input
          className="cq-input"
          type="search"
          placeholder="Filter files"
          aria-label="Filter files"
          value={filter}
          onChange={(event) => onFilter(event.target.value)}
        />
      </span>
      <span className="cq-select-wrap">
        <select
          className="cq-select"
          aria-label="Sort files"
          value={sort}
          // SAFETY: every option's value comes from FILE_SORTS, so the select can only hold a FileSort.
          onChange={(event) => onSort(event.target.value as FileSort)}
        >
          {FILE_SORTS.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
        <Icon name="chevron" />
      </span>
    </div>
    {files.length === 0 ? (
      <div className="cq-state">
        <p>No file matches “{filter}”</p>
      </div>
    ) : (
      <ul className="cq-list cq-scroll">
        {files.map((file) => (
          <li key={file.path}>
            <button
              type="button"
              className="cq-file"
              title={file.path}
              aria-current={file.path === selectedPath ? 'true' : undefined}
              onClick={() => onSelect(file.path)}
            >
              <span className="cq-file-name">{file.name}</span>
              <span className="cq-file-numbers">
                <span
                  className={
                    file.counts.survived > 0 ? 'cq-file-survived' : 'cq-file-survived cq-zero'
                  }
                >
                  {file.counts.survived > 0 && <Icon name="survived" status="survived" />}
                  {formatCount(file.counts.survived)}
                  <span className="cq-sr-only"> survived</span>
                </span>
                <span className="cq-file-score">
                  {file.score === null ? 'N/A' : formatPercent(file.score)}
                </span>
              </span>
              <span className="cq-file-dir">{file.directory}</span>
              <MutantBar counts={file.counts} size="small" />
            </button>
          </li>
        ))}
      </ul>
    )}
  </aside>
);

export default FileList;
