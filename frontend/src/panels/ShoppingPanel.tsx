import { useState } from 'react';
import {
  ShoppingBag,
  Search,
  ExternalLink,
  Award,
  Star,
  CheckCircle2,
  AlertCircle,
  TrendingDown,
  Sparkles,
  Layers,
  ArrowRight
} from 'lucide-react';
import {
  parseShoppingCriteria,
  searchProducts,
  compareProducts,
  type ShoppingCriteria,
  type ProductItem,
  type ComparisonMatrix
} from '@/api';

export default function ShoppingPanel({ token }: { token: string }) {
  const [queryInput, setQueryInput] = useState('mechanical keyboard');
  const [maxBudget, setMaxBudget] = useState<string>('100');
  const [currency, setCurrency] = useState('USD');
  const [sortPref, setSortPref] = useState('best_value');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [activeTab, setActiveTab] = useState<'matrix' | 'cards'>('matrix');
  const [matrix, setMatrix] = useState<ComparisonMatrix | null>(null);
  const [products, setProducts] = useState<ProductItem[]>([]);

  const quickSearches = [
    { label: 'Keyboards under $100', query: 'mechanical keyboard', budget: '100', cur: 'USD' },
    { label: 'ANC Headphones under ₹15,000', query: 'noise cancelling headphones', budget: '15000', cur: 'INR' },
    { label: 'Wireless Mouse under $70', query: 'wireless gaming mouse', budget: '70', cur: 'USD' },
    { label: '4K Monitors under $500', query: '4k usb-c monitor', budget: '500', cur: 'USD' },
  ];

  async function handleSearch(overrideQuery?: string, overrideBudget?: string, overrideCur?: string) {
    const q = overrideQuery ?? queryInput;
    const b = overrideBudget ?? maxBudget;
    const c = overrideCur ?? currency;

    if (!q.trim()) return;

    setLoading(true);
    setError(null);

    try {
      // 1. Parse natural criteria or build from fields
      const criteria: ShoppingCriteria = {
        query: q.trim(),
        maxPrice: b ? parseFloat(b) : null,
        currency: c,
        requiredFeatures: [],
        preferredBrands: [],
        sortPreference: sortPref,
      };

      // 2. Fetch candidates & matrix
      const items = await searchProducts(token, criteria);
      setProducts(items);

      const compMatrix = await compareProducts(token, criteria, items);
      setMatrix(compMatrix);
    } catch (err: any) {
      setError(err?.message || 'Failed to search and compare products.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="panel-container" style={{ padding: '24px 32px', maxWidth: '1280px', margin: '0 auto' }}>
      {/* Header */}
      <div style={{ marginBottom: '24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '8px' }}>
          <ShoppingBag size={24} style={{ color: 'var(--accent, #00f0ff)' }} />
          <h2 style={{ fontSize: '1.4rem', fontWeight: 600, margin: 0 }}>Shopping Research &amp; Comparison</h2>
        </div>
        <p style={{ color: 'rgba(255, 255, 255, 0.65)', fontSize: '0.9rem', margin: 0 }}>
          Verifiable product research with budget constraints, side-by-side spec comparisons, and safe link-based retailer handoff.
        </p>
      </div>

      {/* Search & Filter Bar */}
      <div
        style={{
          background: 'rgba(255, 255, 255, 0.03)',
          border: '1px solid rgba(255, 255, 255, 0.08)',
          borderRadius: '12px',
          padding: '16px 20px',
          marginBottom: '20px',
        }}
      >
        <form
          onSubmit={(e) => {
            e.preventDefault();
            handleSearch();
          }}
          style={{ display: 'flex', flexWrap: 'wrap', gap: '12px', alignItems: 'flex-end' }}
        >
          <div style={{ flex: '2 1 240px' }}>
            <label style={{ display: 'block', fontSize: '0.78rem', color: 'rgba(255, 255, 255, 0.6)', marginBottom: '6px' }}>
              Product Target / Keywords
            </label>
            <div style={{ position: 'relative' }}>
              <Search
                size={16}
                style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)', color: 'rgba(255, 255, 255, 0.4)' }}
              />
              <input
                type="text"
                value={queryInput}
                onChange={(e) => setQueryInput(e.target.value)}
                placeholder="e.g. mechanical keyboard, wireless headphones..."
                style={{
                  width: '100%',
                  padding: '9px 12px 9px 36px',
                  background: 'rgba(0, 0, 0, 0.35)',
                  border: '1px solid rgba(255, 255, 255, 0.15)',
                  borderRadius: '6px',
                  color: '#fff',
                  fontSize: '0.88rem',
                }}
              />
            </div>
          </div>

          <div style={{ flex: '1 1 110px' }}>
            <label style={{ display: 'block', fontSize: '0.78rem', color: 'rgba(255, 255, 255, 0.6)', marginBottom: '6px' }}>
              Max Budget
            </label>
            <input
              type="number"
              value={maxBudget}
              onChange={(e) => setMaxBudget(e.target.value)}
              placeholder="e.g. 100"
              style={{
                width: '100%',
                padding: '9px 12px',
                background: 'rgba(0, 0, 0, 0.35)',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                borderRadius: '6px',
                color: '#fff',
                fontSize: '0.88rem',
              }}
            />
          </div>

          <div style={{ flex: '0 1 95px' }}>
            <label style={{ display: 'block', fontSize: '0.78rem', color: 'rgba(255, 255, 255, 0.6)', marginBottom: '6px' }}>
              Currency
            </label>
            <select
              value={currency}
              onChange={(e) => setCurrency(e.target.value)}
              style={{
                width: '100%',
                padding: '9px 10px',
                background: 'rgba(0, 0, 0, 0.35)',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                borderRadius: '6px',
                color: '#fff',
                fontSize: '0.88rem',
                cursor: 'pointer',
              }}
            >
              <option value="USD">USD ($)</option>
              <option value="INR">INR (₹)</option>
              <option value="EUR">EUR (€)</option>
              <option value="GBP">GBP (£)</option>
            </select>
          </div>

          <div style={{ flex: '1 1 130px' }}>
            <label style={{ display: 'block', fontSize: '0.78rem', color: 'rgba(255, 255, 255, 0.6)', marginBottom: '6px' }}>
              Sort Preference
            </label>
            <select
              value={sortPref}
              onChange={(e) => setSortPref(e.target.value)}
              style={{
                width: '100%',
                padding: '9px 10px',
                background: 'rgba(0, 0, 0, 0.35)',
                border: '1px solid rgba(255, 255, 255, 0.15)',
                borderRadius: '6px',
                color: '#fff',
                fontSize: '0.88rem',
                cursor: 'pointer',
              }}
            >
              <option value="best_value">Best Value (Rating / $)</option>
              <option value="price_asc">Lowest Price First</option>
              <option value="rating_desc">Highest Verified Rating</option>
            </select>
          </div>

          <button
            type="submit"
            disabled={loading || !queryInput.trim()}
            style={{
              padding: '9px 20px',
              background: 'var(--accent, #00f0ff)',
              color: '#000',
              fontWeight: 600,
              border: 'none',
              borderRadius: '6px',
              cursor: loading ? 'not-allowed' : 'pointer',
              display: 'inline-flex',
              alignItems: 'center',
              gap: '6px',
              fontSize: '0.88rem',
              height: '38px',
            }}
          >
            {loading ? 'Searching…' : 'Compare Products'}
          </button>
        </form>

        {/* Quick Suggestion Chips */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '14px', flexWrap: 'wrap' }}>
          <span style={{ fontSize: '0.76rem', color: 'rgba(255, 255, 255, 0.5)' }}>Suggestions:</span>
          {quickSearches.map((qs, i) => (
            <button
              key={i}
              type="button"
              onClick={() => {
                setQueryInput(qs.query);
                setMaxBudget(qs.budget);
                setCurrency(qs.cur);
                handleSearch(qs.query, qs.budget, qs.cur);
              }}
              style={{
                background: 'rgba(255, 255, 255, 0.06)',
                border: '1px solid rgba(255, 255, 255, 0.12)',
                borderRadius: '16px',
                padding: '3px 10px',
                fontSize: '0.78rem',
                color: 'rgba(255, 255, 255, 0.85)',
                cursor: 'pointer',
              }}
            >
              {qs.label}
            </button>
          ))}
        </div>
      </div>

      {error && (
        <div
          style={{
            padding: '12px 16px',
            background: 'rgba(239, 68, 68, 0.15)',
            border: '1px solid rgba(239, 68, 68, 0.4)',
            borderRadius: '8px',
            color: '#fca5a5',
            fontSize: '0.88rem',
            marginBottom: '20px',
            display: 'flex',
            alignItems: 'center',
            gap: '8px',
          }}
        >
          <AlertCircle size={18} /> {error}
        </div>
      )}

      {/* Results Section */}
      {matrix && matrix.items.length > 0 && (
        <div>
          {/* Recommendation Banner */}
          <div
            style={{
              background: 'linear-gradient(135deg, rgba(0, 240, 255, 0.08) 0%, rgba(245, 158, 11, 0.06) 100%)',
              border: '1px solid rgba(0, 240, 255, 0.3)',
              borderRadius: '10px',
              padding: '16px 20px',
              marginBottom: '20px',
            }}
          >
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '6px' }}>
              <Sparkles size={18} style={{ color: 'var(--accent, #00f0ff)' }} />
              <strong style={{ fontSize: '0.94rem', color: '#fff' }}>J.A.R.V.I.S. Recommendation</strong>
            </div>
            <p style={{ margin: 0, fontSize: '0.88rem', color: 'rgba(255, 255, 255, 0.9)', lineHeight: 1.5 }}>
              {matrix.recommendationSummary}
            </p>
          </div>

          {/* View Toggle */}
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <div style={{ fontSize: '0.85rem', color: 'rgba(255, 255, 255, 0.6)' }}>
              Showing <strong>{matrix.items.length}</strong> verified product candidates
            </div>
            <div style={{ display: 'inline-flex', background: 'rgba(255, 255, 255, 0.06)', borderRadius: '6px', padding: '2px' }}>
              <button
                type="button"
                onClick={() => setActiveTab('matrix')}
                style={{
                  padding: '5px 12px',
                  background: activeTab === 'matrix' ? 'rgba(0, 240, 255, 0.2)' : 'transparent',
                  color: activeTab === 'matrix' ? '#00f0ff' : 'rgba(255, 255, 255, 0.7)',
                  border: 'none',
                  borderRadius: '4px',
                  fontSize: '0.82rem',
                  fontWeight: 500,
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '5px',
                }}
              >
                <Layers size={14} /> Spec Matrix
              </button>
              <button
                type="button"
                onClick={() => setActiveTab('cards')}
                style={{
                  padding: '5px 12px',
                  background: activeTab === 'cards' ? 'rgba(0, 240, 255, 0.2)' : 'transparent',
                  color: activeTab === 'cards' ? '#00f0ff' : 'rgba(255, 255, 255, 0.7)',
                  border: 'none',
                  borderRadius: '4px',
                  fontSize: '0.82rem',
                  fontWeight: 500,
                  cursor: 'pointer',
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '5px',
                }}
              >
                <ShoppingBag size={14} /> Cards View
              </button>
            </div>
          </div>

          {/* Comparison Matrix Table */}
          {activeTab === 'matrix' && (
            <div
              style={{
                overflowX: 'auto',
                background: 'rgba(0, 0, 0, 0.25)',
                border: '1px solid rgba(255, 255, 255, 0.08)',
                borderRadius: '10px',
                marginBottom: '20px',
              }}
            >
              <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left', fontSize: '0.86rem' }}>
                <thead>
                  <tr style={{ background: 'rgba(255, 255, 255, 0.04)', borderBottom: '1px solid rgba(255, 255, 255, 0.1)' }}>
                    <th style={{ padding: '12px 16px', minWidth: '160px', color: 'rgba(255, 255, 255, 0.6)' }}>Feature / Item</th>
                    {matrix.items.map((item) => (
                      <th key={item.id} style={{ padding: '12px 16px', minWidth: '220px', verticalAlign: 'top' }}>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                          <span style={{ fontWeight: 600, color: '#fff' }}>{item.title}</span>
                          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                            {item.id === matrix.bestValueId && (
                              <span
                                style={{
                                  background: 'rgba(245, 158, 11, 0.2)',
                                  border: '1px solid rgba(245, 158, 11, 0.5)',
                                  color: '#fbbf24',
                                  fontSize: '0.72rem',
                                  padding: '1px 6px',
                                  borderRadius: '4px',
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: '3px',
                                }}
                              >
                                <Award size={11} /> Best Value
                              </span>
                            )}
                            {item.id === matrix.topRatedId && (
                              <span
                                style={{
                                  background: 'rgba(0, 240, 255, 0.2)',
                                  border: '1px solid rgba(0, 240, 255, 0.5)',
                                  color: '#00f0ff',
                                  fontSize: '0.72rem',
                                  padding: '1px 6px',
                                  borderRadius: '4px',
                                  display: 'inline-flex',
                                  alignItems: 'center',
                                  gap: '3px',
                                }}
                              >
                                <Star size={11} /> Top Rated
                              </span>
                            )}
                          </div>
                        </div>
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {/* Price Row */}
                  <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                    <td style={{ padding: '12px 16px', fontWeight: 600, color: 'rgba(255, 255, 255, 0.8)' }}>Price</td>
                    {matrix.items.map((item) => (
                      <td key={item.id} style={{ padding: '12px 16px', fontSize: '1rem', fontWeight: 700, color: 'var(--accent, #00f0ff)' }}>
                        {item.currency} {item.price.toFixed(2)}
                      </td>
                    ))}
                  </tr>

                  {/* Rating Row */}
                  <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                    <td style={{ padding: '12px 16px', fontWeight: 600, color: 'rgba(255, 255, 255, 0.8)' }}>Rating</td>
                    {matrix.items.map((item) => (
                      <td key={item.id} style={{ padding: '12px 16px' }}>
                        {item.rating ? (
                          <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                            <Star size={14} style={{ fill: '#fbbf24', color: '#fbbf24' }} />
                            <strong>{item.rating}</strong>
                            <span style={{ color: 'rgba(255, 255, 255, 0.45)', fontSize: '0.8rem' }}>
                              ({item.reviewCount?.toLocaleString()} reviews)
                            </span>
                          </div>
                        ) : (
                          <span style={{ color: 'rgba(255, 255, 255, 0.3)' }}>N/A</span>
                        )}
                      </td>
                    ))}
                  </tr>

                  {/* Retailer Row */}
                  <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                    <td style={{ padding: '12px 16px', fontWeight: 600, color: 'rgba(255, 255, 255, 0.8)' }}>Retailer</td>
                    {matrix.items.map((item) => (
                      <td key={item.id} style={{ padding: '12px 16px', color: 'rgba(255, 255, 255, 0.85)' }}>
                        {item.retailer}
                      </td>
                    ))}
                  </tr>

                  {/* Stock Row */}
                  <tr style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                    <td style={{ padding: '12px 16px', fontWeight: 600, color: 'rgba(255, 255, 255, 0.8)' }}>Availability</td>
                    {matrix.items.map((item) => (
                      <td key={item.id} style={{ padding: '12px 16px' }}>
                        {item.inStock ? (
                          <span style={{ color: '#4ade80', display: 'inline-flex', alignItems: 'center', gap: '4px' }}>
                            <CheckCircle2 size={14} /> In Stock
                          </span>
                        ) : (
                          <span style={{ color: '#f87171' }}>Out of Stock</span>
                        )}
                      </td>
                    ))}
                  </tr>

                  {/* Specifications (missing fields rendered as N/A, never hallucinated) */}
                  {matrix.comparedSpecs.map((specKey) => (
                    <tr key={specKey} style={{ borderBottom: '1px solid rgba(255, 255, 255, 0.05)' }}>
                      <td style={{ padding: '12px 16px', fontWeight: 600, color: 'rgba(255, 255, 255, 0.8)' }}>{specKey}</td>
                      {matrix.items.map((item) => {
                        const val = item.specs?.[specKey];
                        return (
                          <td key={item.id} style={{ padding: '12px 16px', color: val ? 'rgba(255, 255, 255, 0.85)' : 'rgba(255, 255, 255, 0.3)' }}>
                            {val || '—'}
                          </td>
                        );
                      })}
                    </tr>
                  ))}

                  {/* Action Link Row */}
                  <tr>
                    <td style={{ padding: '16px' }}></td>
                    {matrix.items.map((item) => (
                      <td key={item.id} style={{ padding: '16px' }}>
                        <a
                          href={item.url}
                          target="_blank"
                          rel="noopener noreferrer"
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px',
                            background: 'rgba(0, 240, 255, 0.12)',
                            border: '1px solid rgba(0, 240, 255, 0.35)',
                            borderRadius: '6px',
                            padding: '8px 14px',
                            color: '#00f0ff',
                            textDecoration: 'none',
                            fontSize: '0.84rem',
                            fontWeight: 600,
                          }}
                        >
                          View on {item.retailer} <ExternalLink size={13} />
                        </a>
                      </td>
                    ))}
                  </tr>
                </tbody>
              </table>
            </div>
          )}

          {/* Cards View */}
          {activeTab === 'cards' && (
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(320px, 1fr))', gap: '16px', marginBottom: '20px' }}>
              {matrix.items.map((item) => (
                <div
                  key={item.id}
                  style={{
                    background: 'rgba(255, 255, 255, 0.03)',
                    border: '1px solid rgba(255, 255, 255, 0.08)',
                    borderRadius: '10px',
                    padding: '18px 20px',
                    display: 'flex',
                    flexDirection: 'column',
                    justifyContent: 'space-between',
                  }}
                >
                  <div>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
                      <span
                        style={{
                          background: 'rgba(255, 255, 255, 0.08)',
                          color: 'rgba(255, 255, 255, 0.7)',
                          fontSize: '0.74rem',
                          padding: '2px 8px',
                          borderRadius: '4px',
                          fontWeight: 500,
                        }}
                      >
                        {item.retailer}
                      </span>
                      {item.id === matrix.bestValueId && (
                        <span style={{ color: '#fbbf24', fontSize: '0.74rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '3px' }}>
                          <Award size={13} /> Best Value
                        </span>
                      )}
                      {item.id === matrix.topRatedId && item.id !== matrix.bestValueId && (
                        <span style={{ color: '#00f0ff', fontSize: '0.74rem', fontWeight: 600, display: 'flex', alignItems: 'center', gap: '3px' }}>
                          <Star size={13} /> Top Rated
                        </span>
                      )}
                    </div>

                    <h3 style={{ fontSize: '1rem', fontWeight: 600, margin: '0 0 10px 0', color: '#fff', lineHeight: 1.4 }}>
                      {item.title}
                    </h3>

                    <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', marginBottom: '12px' }}>
                      <span style={{ fontSize: '1.25rem', fontWeight: 700, color: 'var(--accent, #00f0ff)' }}>
                        {item.currency} {item.price.toFixed(2)}
                      </span>
                      {item.rating && (
                        <span style={{ fontSize: '0.82rem', color: '#fbbf24', display: 'flex', alignItems: 'center', gap: '3px' }}>
                          ★ {item.rating} <span style={{ color: 'rgba(255, 255, 255, 0.4)' }}>({item.reviewCount})</span>
                        </span>
                      )}
                    </div>

                    {item.highlights && item.highlights.length > 0 && (
                      <ul style={{ margin: '0 0 16px 0', paddingLeft: '18px', fontSize: '0.82rem', color: 'rgba(255, 255, 255, 0.75)' }}>
                        {item.highlights.map((hl, idx) => (
                          <li key={idx} style={{ marginBottom: '4px' }}>
                            {hl}
                          </li>
                        ))}
                      </ul>
                    )}
                  </div>

                  <a
                    href={item.url}
                    target="_blank"
                    rel="noopener noreferrer"
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      gap: '6px',
                      background: 'rgba(0, 240, 255, 0.1)',
                      border: '1px solid rgba(0, 240, 255, 0.3)',
                      borderRadius: '6px',
                      padding: '9px 16px',
                      color: '#00f0ff',
                      textDecoration: 'none',
                      fontSize: '0.85rem',
                      fontWeight: 600,
                      marginTop: '8px',
                    }}
                  >
                    View on {item.retailer} <ExternalLink size={14} />
                  </a>
                </div>
              ))}
            </div>
          )}

          {/* Checkout Handoff Safeguard Notice */}
          <div
            style={{
              padding: '12px 16px',
              background: 'rgba(255, 255, 255, 0.02)',
              border: '1px solid rgba(255, 255, 255, 0.06)',
              borderRadius: '8px',
              fontSize: '0.8rem',
              color: 'rgba(255, 255, 255, 0.5)',
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <CheckCircle2 size={15} style={{ color: '#4ade80' }} />
            <span>
              <strong>Safe Purchase Handoff:</strong> JARVIS does not store payment card information or execute automated checkouts. Clicking a product securely opens the vendor’s official store page.
            </span>
          </div>
        </div>
      )}

      {!loading && (!matrix || matrix.items.length === 0) && (
        <div
          style={{
            textAlign: 'center',
            padding: '60px 20px',
            background: 'rgba(255, 255, 255, 0.02)',
            borderRadius: '12px',
            border: '1px dashed rgba(255, 255, 255, 0.1)',
          }}
        >
          <ShoppingBag size={42} style={{ color: 'rgba(255, 255, 255, 0.2)', marginBottom: '12px' }} />
          <h3 style={{ fontSize: '1.05rem', fontWeight: 500, margin: '0 0 6px 0', color: 'rgba(255, 255, 255, 0.7)' }}>
            Search and compare any product
          </h3>
          <p style={{ fontSize: '0.85rem', color: 'rgba(255, 255, 255, 0.45)', margin: 0 }}>
            Enter a product category or click one of the suggestion chips above to view side-by-side spec comparisons.
          </p>
        </div>
      )}
    </div>
  );
}
