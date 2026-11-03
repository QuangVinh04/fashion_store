import type { SizeRecommendationRegionResult, SizeRecommendationFit, SizeRecommendationRegion } from '../../api/types';

const REGION_LABELS: Record<SizeRecommendationRegion, string> = {
  SHOULDER: 'Vai',
  CHEST: 'Ngực',
  WAIST: 'Eo',
  HIP: 'Mông',
  LENGTH: 'Dài áo',
  INSEAM: 'Dài quần',
};

const FIT_CONFIG: Record<SizeRecommendationFit, { label: string; color: string; bg: string; border: string }> = {
  GOOD: { label: 'Vừa', color: '#15803d', bg: '#dcfce7', border: '#22c55e' },
  SNUG: { label: 'Hơi ôm', color: '#a16207', bg: '#fef9c3', border: '#eab308' },
  TIGHT: { label: 'Chật', color: '#b91c1c', bg: '#fee2e2', border: '#ef4444' },
  LOOSE: { label: 'Rộng', color: '#475569', bg: '#f1f5f9', border: '#94a3b8' },
};

function formatDetail(fit: SizeRecommendationFit, diffCm: number): string {
  const absDiff = Math.abs(Math.round(diffCm * 10) / 10);
  switch (fit) {
    case 'TIGHT':
      return `chật (size nhỏ hơn bạn ${absDiff} cm)`;
    case 'SNUG':
      return `hơi ôm (size nhỏ hơn bạn ${absDiff} cm)`;
    case 'LOOSE':
      return `rộng (size lớn hơn bạn ${absDiff} cm)`;
    case 'GOOD':
      return absDiff === 0 ? 'vừa (khớp số đo bạn)' : `vừa (chênh lệch ${absDiff} cm)`;
  }
}

export default function BodyFitMap({ regions }: { regions?: SizeRecommendationRegionResult[] }) {
  if (!regions || regions.length === 0) return null;

  const regionMap = new Map<SizeRecommendationRegion, SizeRecommendationRegionResult>();
  regions.forEach(r => regionMap.set(r.region, r));

  const getFit = (region: SizeRecommendationRegion) => regionMap.get(region)?.fit;
  const getColor = (region: SizeRecommendationRegion) => {
    const fit = getFit(region);
    return fit ? FIT_CONFIG[fit].border : '#e2e8f0';
  };
  const getFill = (region: SizeRecommendationRegion) => {
    const fit = getFit(region);
    return fit ? FIT_CONFIG[fit].bg : '#f8fafc';
  };

  const summary = regions
    .map(r => `${REGION_LABELS[r.region]}: ${formatDetail(r.fit, r.differenceCm)}`)
    .join('; ');

  return (
    <div className="pd-fitmap-container">
      <div className="pd-fitmap-header">
        <h4 className="pd-fitmap-title">Độ vừa vặn theo từng vùng</h4>
        <div className="pd-fitmap-legend" aria-hidden="true">
          {(['GOOD', 'SNUG', 'TIGHT', 'LOOSE'] as SizeRecommendationFit[]).map(fit => (
            <span key={fit} className="pd-legend-item">
              <span className="pd-legend-dot" style={{ backgroundColor: FIT_CONFIG[fit].border }} />
              {FIT_CONFIG[fit].label}
            </span>
          ))}
        </div>
      </div>

      <div className="pd-fitmap-content">
        <div className="pd-fitmap-visual" role="img" aria-label={`Bản đồ độ vừa vặn cơ thể. ${summary}`}>
          <svg
            viewBox="0 0 200 300"
            className="pd-fitmap-svg"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            {/* Base Body Silhouette */}
            <g className="pd-body-base" stroke="#cbd5e1" strokeWidth="1" fill="#f8fafc">
              {/* Head */}
              <circle cx="100" cy="22" r="14" />
              {/* Neck */}
              <path d="M 94 36 L 106 36 L 106 44 L 94 44 Z" />
              {/* Body Outline contour */}
              <path
                d="M 68 44
                   C 50 48, 40 60, 36 90
                   C 34 105, 38 120, 42 135
                   L 52 135
                   L 50 100
                   C 54 110, 60 120, 62 140
                   C 60 155, 54 170, 52 190
                   L 72 280
                   L 94 280
                   L 98 175
                   L 102 175
                   L 106 280
                   L 128 280
                   L 148 190
                   C 146 170, 140 155, 138 140
                   C 140 120, 146 110, 150 100
                   L 148 135
                   L 158 135
                   C 162 120, 166 105, 164 90
                   C 160 60, 150 48, 132 44
                   Z"
              />
            </g>

            {/* SHOULDER Region */}
            <g className="pd-fit-region">
              <rect
                x="44"
                y="46"
                width="112"
                height="16"
                rx="8"
                fill={getFill('SHOULDER')}
                stroke={getColor('SHOULDER')}
                strokeWidth={getFit('SHOULDER') ? 2 : 1}
              />
              <text x="100" y="58" textAnchor="middle" fontSize="9" fontWeight="600" fill={getFit('SHOULDER') ? '#1e293b' : '#94a3b8'}>
                Vai
              </text>
            </g>

            {/* CHEST Region */}
            <g className="pd-fit-region">
              <rect
                x="56"
                y="68"
                width="88"
                height="26"
                rx="6"
                fill={getFill('CHEST')}
                stroke={getColor('CHEST')}
                strokeWidth={getFit('CHEST') ? 2 : 1}
              />
              <text x="100" y="84" textAnchor="middle" fontSize="9" fontWeight="600" fill={getFit('CHEST') ? '#1e293b' : '#94a3b8'}>
                Ngực
              </text>
            </g>

            {/* WAIST Region */}
            <g className="pd-fit-region">
              <rect
                x="64"
                y="100"
                width="72"
                height="22"
                rx="6"
                fill={getFill('WAIST')}
                stroke={getColor('WAIST')}
                strokeWidth={getFit('WAIST') ? 2 : 1}
              />
              <text x="100" y="114" textAnchor="middle" fontSize="9" fontWeight="600" fill={getFit('WAIST') ? '#1e293b' : '#94a3b8'}>
                Eo
              </text>
            </g>

            {/* HIP Region */}
            <g className="pd-fit-region">
              <rect
                x="58"
                y="128"
                width="84"
                height="26"
                rx="6"
                fill={getFill('HIP')}
                stroke={getColor('HIP')}
                strokeWidth={getFit('HIP') ? 2 : 1}
              />
              <text x="100" y="144" textAnchor="middle" fontSize="9" fontWeight="600" fill={getFit('HIP') ? '#1e293b' : '#94a3b8'}>
                Mông
              </text>
            </g>

            {/* INSEAM Region (Inner leg) */}
            <g className="pd-fit-region">
              <rect
                x="88"
                y="172"
                width="24"
                height="98"
                rx="5"
                fill={getFill('INSEAM')}
                stroke={getColor('INSEAM')}
                strokeWidth={getFit('INSEAM') ? 2 : 1}
              />
              <text
                x="100"
                y="225"
                textAnchor="middle"
                fontSize="9"
                fontWeight="600"
                fill={getFit('INSEAM') ? '#1e293b' : '#94a3b8'}
                transform="rotate(-90 100 225)"
              >
                Dài quần
              </text>
            </g>

            {/* LENGTH Indicator (Side vertical guide from shoulder to hem) */}
            <g className="pd-fit-region">
              <path
                d="M 172 48 L 180 48 L 180 160 L 172 160"
                fill="none"
                stroke={getColor('LENGTH')}
                strokeWidth={getFit('LENGTH') ? 2.5 : 1.5}
                strokeDasharray={getFit('LENGTH') ? undefined : '3 3'}
              />
              <rect
                x="166"
                y="94"
                width="28"
                height="20"
                rx="4"
                fill={getFill('LENGTH')}
                stroke={getColor('LENGTH')}
                strokeWidth={getFit('LENGTH') ? 1.5 : 1}
              />
              <text x="180" y="107" textAnchor="middle" fontSize="8" fontWeight="600" fill={getFit('LENGTH') ? '#1e293b' : '#94a3b8'}>
                Dài áo
              </text>
            </g>
          </svg>
        </div>

        {/* Text Details List */}
        <div className="pd-fitmap-list" tabIndex={0} role="region" aria-label="Chi tiết độ vừa vặn từng vùng">
          {regions.map(r => {
            const fitConfig = FIT_CONFIG[r.fit];
            return (
              <div key={r.region} className="pd-fitmap-item">
                <span className="pd-fitmap-dot" style={{ backgroundColor: fitConfig.border }} aria-hidden="true" />
                <div className="pd-fitmap-desc">
                  <strong>{REGION_LABELS[r.region]}:</strong> {formatDetail(r.fit, r.differenceCm)}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    </div>
  );
}
