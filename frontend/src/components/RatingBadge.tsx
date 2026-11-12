import React from 'react';
import type { Rating } from '../mutants';

interface RatingBadgeProps {
  rating: Rating;
  large?: boolean;
}

const RatingBadge: React.FC<RatingBadgeProps> = ({ rating, large = false }) => (
  <span
    className={large ? 'cq-rating cq-rating-large' : 'cq-rating'}
    data-rating={rating}
    title={`Rating ${rating}`}
  >
    {rating}
  </span>
);

export default RatingBadge;
