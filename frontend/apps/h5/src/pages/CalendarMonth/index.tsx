// P10 · 日历月视图 · CalendarMonth
// Archive: design/mockups/wrongbook/_archive/10_calendar_month.html
// Mood B · pure-warm · #F2F2F7 bg + iOS nav 玻璃态
// AC 覆盖: AC-P10-001 ~ AC-P10-009
// testid 根: p10-*

import React, { useState, useCallback, useMemo } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import s from './CalendarMonth.module.css';

// ─── Types ────────────────────────────────────────────────────────────────────
type RelationType = 'STUDY' | 'EXAM' | 'FAMILY';
type SubjectType = 'math' | 'physics' | 'chemistry' | 'english';
type TLevel = 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';

interface CalendarCellEvent {
  eventId: string;
  relationType: RelationType;
  subject?: SubjectType;
  tLevel?: TLevel;
  startAt: string;
}

interface CalendarCell {
  date: string;          // "YYYY-MM-DD"
  inMonth: boolean;
  isToday: boolean;
  events: CalendarCellEvent[];
}

interface CalendarMonthResp {
  month: string;         // "YYYY-MM"
  tzOffset: string;      // "Asia/Shanghai"
  today: string;         // "YYYY-MM-DD"
  cells: CalendarCell[]; // length = 42
}

// ─── Helpers ──────────────────────────────────────────────────────────────────
function toYYYYMM(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`;
}

function parseTitleFromMonth(month: string): string {
  const [y, m] = month.split('-').map(Number);
  return `${y} 年 ${m} 月`;
}

function prevMonth(month: string): string {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(y, m - 2, 1);
  return toYYYYMM(d);
}

function nextMonth(month: string): string {
  const [y, m] = month.split('-').map(Number);
  const d = new Date(y, m, 1);
  return toYYYYMM(d);
}

function currentMonth(): string {
  return toYYYYMM(new Date());
}

function isWeekend(dateStr: string): boolean {
  const d = new Date(dateStr + 'T00:00:00');
  const day = d.getDay(); // 0=Sun, 6=Sat
  return day === 0 || day === 6;
}

// Subject → dot CSS class
function dotClass(event: CalendarCellEvent, s_: typeof s): string {
  if (event.relationType === 'EXAM') return s_.dotExam;
  if (event.relationType === 'FAMILY') return s_.dotFamily;
  if (event.subject === 'math') return s_.dotMath;
  if (event.subject === 'physics') return s_.dotPhysics;
  if (event.subject === 'chemistry') return s_.dotChem;
  if (event.subject === 'english') return s_.dotEnglish;
  return s_.dotExam;
}

// Subject → inline color (for day-list bar & legend)
function subjectColor(event: CalendarCellEvent): string {
  if (event.relationType === 'EXAM') return '#FF3B30';
  if (event.relationType === 'FAMILY') return '#FF9500';
  if (event.subject === 'math') return 'var(--tkn-subject-math, #C41E3A)';
  if (event.subject === 'physics') return 'var(--tkn-subject-physics, #0057B7)';
  if (event.subject === 'chemistry') return 'var(--tkn-subject-chemistry, #1A6B3A)';
  if (event.subject === 'english') return 'var(--tkn-subject-english, #9C4F00)';
  return '#8E8E93';
}

// API fetch
async function fetchCalendar(month: string): Promise<CalendarMonthResp> {
  const res = await fetch(`/api/calendar/events?month=${month}&tz=Asia/Shanghai`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<CalendarMonthResp>;
}

// ─── Main Page ────────────────────────────────────────────────────────────────
export const CalendarMonthPage: React.FC = () => {
  const nav = useNavigate();
  const [searchParams] = useSearchParams();

  const [month, setMonth] = useState<string>(
    searchParams.get('month') ?? currentMonth(),
  );
  const [selectedCellIndex, setSelectedCellIndex] = useState<number | null>(null);
  const [showStudy, setShowStudy] = useState(true);

  const isCurrentMonth = month === currentMonth();

  const { data, isLoading, isError, refetch } = useQuery<CalendarMonthResp>({
    queryKey: ['calendar', month],
    queryFn: () => fetchCalendar(month),
    staleTime: 30_000,
  });

  // AC-P10-005 · Filter logic (FILTER_CHANGED state — local only)
  const cells = useMemo<CalendarCell[]>(() => {
    if (!data?.cells) return [];
    if (showStudy) return data.cells;
    return data.cells.map((c) => ({
      ...c,
      events: c.events.filter((e) => e.relationType !== 'STUDY'),
    }));
  }, [data, showStudy]);

  const totalEvents = cells.reduce((sum, c) => sum + c.events.length, 0);
  const isEmpty = !isLoading && !isError && data && totalEvents === 0;

  // AC-P10-006 · Month navigation
  const handlePrev = useCallback(() => {
    setMonth((m) => prevMonth(m));
    setSelectedCellIndex(null);
  }, []);

  const handleNext = useCallback(() => {
    setMonth((m) => nextMonth(m));
    setSelectedCellIndex(null);
  }, []);

  const handleToday = useCallback(() => {
    setMonth(currentMonth());
    setSelectedCellIndex(null);
  }, []);

  // AC-P10-005 · Cell tap → P11
  const handleCellTap = useCallback(
    (cellIdx: number, cell: CalendarCell) => {
      if (cell.events.length === 0) {
        setSelectedCellIndex(cellIdx);
        return;
      }
      // Navigate to P11 with first event + from=CAL
      const firstEvent = cell.events[0];
      nav(`/event/${firstEvent.eventId}?from=CAL`);
    },
    [nav],
  );

  // Weekend column indices (0-indexed): col 5 = Sat, col 6 = Sun (Mon-first layout)
  const weekendCols = new Set([5, 6]);

  // Render 42 cells
  const renderCells = () => {
    if (isLoading) {
      return Array.from({ length: 42 }, (_, i) => (
        <div
          key={i}
          className={s.skeletonCell}
          data-testid={`p10-month-grid-skeleton-${i + 1}`}
          aria-hidden="true"
        />
      ));
    }

    return cells.map((cell, idx) => {
      const colIdx = idx % 7;
      const isWknd = weekendCols.has(colIdx);
      const visibleEvents = cell.events.slice(0, 3);
      const overflow = cell.events.length > 3 ? cell.events.length - 3 : 0;
      const totalCount = cell.events.length;

      let dayNumClass = s.dayNum;
      if (cell.isToday && isWknd) dayNumClass = s.dayNumTodayWeekend;
      else if (cell.isToday) dayNumClass = s.dayNumToday;
      else if (!cell.inMonth) dayNumClass = `${s.dayNum} ${s.dayNumOutOfMonth}`;
      else if (isWknd) dayNumClass = `${s.dayNum} ${s.dayNumWeekend}`;

      const cellClass = [
        s.cell,
        cell.isToday ? s.cellToday : '',
        !cell.inMonth ? s.cellOutOfMonth : '',
      ].filter(Boolean).join(' ');

      const [, , dayStr] = cell.date.split('-');
      const dayNum = parseInt(dayStr, 10);

      return (
        <div
          key={cell.date}
          className={cellClass}
          role="gridcell"
          aria-label={
            cell.isToday
              ? `今日 ${parseTitleFromMonth(month).replace(' 年 ', '年').replace(' 月', '月')} ${dayNum}日 · ${totalCount} 个事件`
              : `${dayNum}日 · ${totalCount} 个事件`
          }
          aria-selected={selectedCellIndex === idx}
          tabIndex={0}
          onClick={() => handleCellTap(idx, cell)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' || e.key === ' ') {
              e.preventDefault();
              handleCellTap(idx, cell);
            }
          }}
          data-testid={`p10-month-grid-cell-${idx + 1}`}
          data-out-of-month={!cell.inMonth ? 'true' : undefined}
          data-today={cell.isToday ? 'true' : undefined}
        >
          {/* Today marker */}
          {cell.isToday && (
            <span
              data-testid={`p10-month-grid-cell-${idx + 1}-today-marker`}
              className={dayNumClass}
              aria-current="date"
            >
              {dayNum}
            </span>
          )}
          {!cell.isToday && (
            <span className={dayNumClass}>{dayNum}</span>
          )}

          {/* Event count bar */}
          {totalCount > 0 && (
            <span className={cell.isToday ? `${s.eventBar} ${s.eventBarToday}` : s.eventBar}>
              {totalCount}
            </span>
          )}

          {/* Dots row */}
          {cell.events.length > 0 && (
            <div className={s.dots}>
              {visibleEvents.map((ev, di) => (
                <span
                  key={ev.eventId}
                  className={`${s.dot} ${dotClass(ev, s)}`}
                  data-testid={`p10-month-grid-cell-${idx + 1}-dot-${di + 1}`}
                  aria-hidden="true"
                />
              ))}
              {overflow > 0 && (
                <span
                  className={s.overflowBadge}
                  data-testid={`p10-month-grid-cell-${idx + 1}-overflow`}
                  aria-hidden="true"
                >
                  +{overflow}
                </span>
              )}
            </div>
          )}
        </div>
      );
    });
  };

  // Selected cell data
  const selectedCell = selectedCellIndex !== null ? cells[selectedCellIndex] : null;

  return (
    <div className={s.root} data-mood="B" data-testid="p10-root">
      {/* ── Navbar ─────────────────────────────────────────── */}
      <header className={s.navbar}>
        <div className={s.navTop}>
          <button
            className={s.back}
            onClick={() => nav('/')}
            aria-label="返回首页"
          >
            <svg width="22" height="22" viewBox="0 0 22 22" fill="none" aria-hidden="true">
              <path d="M14 5l-6 6 6 6" stroke="#007AFF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
            首页
          </button>

          <div className={s.navCenter}>
            <div
              className={s.navTitle}
              data-testid="p10-month-nav-title"
              aria-live="polite"
            >
              {parseTitleFromMonth(month)}
            </div>
            <div className={s.navSub}>Asia/Shanghai</div>
          </div>

          <button className={s.navIconBtn} aria-label="新建事件">
            <svg width="16" height="16" viewBox="0 0 16 16" fill="none" aria-hidden="true">
              <path d="M8 3V13M3 8H13" stroke="#1C1C1E" strokeWidth="1.6" strokeLinecap="round"/>
            </svg>
          </button>
        </div>

        <div className={s.subNav}>
          <div className={s.segControl} role="group" aria-label="视图切换">
            <button className={s.segItem} aria-label="日视图">日</button>
            <button className={s.segItem} aria-label="周视图">周</button>
            <button className={`${s.segItem} ${s.segItemActive}`} aria-label="月视图" aria-pressed="true">月</button>
          </div>
          <div className={s.spacer} />
          <button
            className={s.filterBtn}
            onClick={() => setShowStudy((v) => !v)}
            aria-label={`${showStudy ? '隐藏' : '显示'}复习节点`}
            aria-pressed={showStudy}
            data-testid="p10-filter-study"
          >
            <svg width="11" height="11" viewBox="0 0 11 11" fill="none" aria-hidden="true">
              <path d="M1 2H10M3 5.5H8M4.5 9H6.5" stroke="#007AFF" strokeWidth="1.3" strokeLinecap="round"/>
            </svg>
            显示复习 · {showStudy ? '开' : '关'}
          </button>
        </div>
      </header>

      {/* ── Body: Month Nav + Grid + Legend ──────────────── */}
      <div className={s.body}>
        {/* B1 · MonthNav */}
        <nav
          className={s.monthNav}
          role="navigation"
          aria-label="月份切换"
          data-testid="p10-month-nav"
        >
          <button
            className={s.monthNavBtn}
            onClick={handlePrev}
            aria-label="上一月"
            data-testid="p10-month-nav-prev"
          >
            <svg width="10" height="16" viewBox="0 0 10 16" fill="none" aria-hidden="true">
              <path d="M8 2L2 8l6 6" stroke="#007AFF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>

          <div style={{ textAlign: 'center' }}>
            {!isCurrentMonth && (
              <button
                className={s.todayLink}
                onClick={handleToday}
                data-testid="p10-month-nav-today"
              >
                回到今天
              </button>
            )}
          </div>

          <button
            className={s.monthNavBtn}
            onClick={handleNext}
            aria-label="下一月"
            data-testid="p10-month-nav-next"
          >
            <svg width="10" height="16" viewBox="0 0 10 16" fill="none" aria-hidden="true">
              <path d="M2 2l6 6-6 6" stroke="#007AFF" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/>
            </svg>
          </button>
        </nav>

        {/* B2 · WeekHeader */}
        <div
          className={s.weekHeader}
          role="row"
          aria-label="星期表头"
          data-testid="p10-week-header"
        >
          <div>一</div>
          <div>二</div>
          <div>三</div>
          <div>四</div>
          <div>五</div>
          <div className={s.weekDayWknd}>六</div>
          <div className={s.weekDayWknd}>日</div>
        </div>

        {/* B3 · MonthGrid */}
        {isError ? (
          <div
            className={s.errorState}
            data-testid="p10-month-grid-error"
            role="alert"
          >
            <div className={s.errorText}>加载失败</div>
            <button className={s.retryBtn} onClick={() => refetch()}>重试</button>
          </div>
        ) : (
          <div
            className={isLoading ? s.skeletonGrid : s.monthGrid}
            role="grid"
            aria-label={`${parseTitleFromMonth(month)}日历`}
            data-testid="p10-month-grid"
            data-state={isLoading ? 'loading' : 'ready'}
          >
            {renderCells()}
          </div>
        )}

        {/* EMPTY hint (doesn't block grid) */}
        {isEmpty && (
          <div className={s.emptyHint} role="status" aria-label="本月没有排期">
            本月没有排期
          </div>
        )}

        {/* Day event bottom panel (when cell with 0 events selected) */}
        {selectedCell && selectedCell.events.length === 0 && (
          <div
            className={s.dayList}
            role="complementary"
            aria-label="今日无排期"
          >
            <div className={s.dayListHead}>
              <div>
                <span className={s.dayListTitle}>
                  {parseInt(selectedCell.date.split('-')[1], 10)} 月{' '}
                  {parseInt(selectedCell.date.split('-')[2], 10)} 日
                </span>
              </div>
            </div>
            <div style={{ fontSize: 13, color: 'var(--ios-ter, #8E8E93)', textAlign: 'center', padding: '16px 0' }}>
              今日无排期
            </div>
          </div>
        )}

        {/* Day event bottom panel (when cell with events selected - show list) */}
        {selectedCell && selectedCell.events.length > 0 && (
          <div
            className={s.dayList}
            role="complementary"
            aria-label={`${parseInt(selectedCell.date.split('-')[1], 10)} 月 ${parseInt(selectedCell.date.split('-')[2], 10)} 日事件列表`}
          >
            <div className={s.dayListHead}>
              <div>
                <span className={s.dayListTitle}>
                  {parseInt(selectedCell.date.split('-')[1], 10)} 月{' '}
                  {parseInt(selectedCell.date.split('-')[2], 10)} 日
                </span>
                <span className={s.dayListSub}>{selectedCell.events.length} 条</span>
              </div>
              <button className={s.dayListMore} onClick={() => nav('/review')}>全部 ›</button>
            </div>

            {selectedCell.events.map((ev) => (
              <div
                key={ev.eventId}
                className={s.eventRow}
                role="listitem"
                onClick={() => nav(`/event/${ev.eventId}?from=CAL`)}
                tabIndex={0}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') nav(`/event/${ev.eventId}?from=CAL`);
                }}
              >
                <div
                  className={s.eventRowBar}
                  style={{ background: subjectColor(ev) }}
                  aria-hidden="true"
                />
                <div className={s.eventRowContent}>
                  <div className={s.eventRowTitle}>
                    {ev.relationType === 'STUDY' && ev.tLevel && (
                      <span className={s.studyTag}>{ev.tLevel} 复习</span>
                    )}
                    {ev.relationType === 'EXAM' && (
                      <span
                        className={s.eventTag}
                        style={{ background: 'rgba(255,59,48,0.14)', color: '#C71F47' }}
                      >
                        考试
                      </span>
                    )}
                    {ev.relationType === 'FAMILY' && (
                      <span
                        className={s.eventTag}
                        style={{ background: 'rgba(52,199,89,0.14)', color: '#1E7E34' }}
                      >
                        家庭
                      </span>
                    )}
                  </div>
                  <div className={s.eventMeta}>
                    <span>
                      {ev.subject === 'math' ? '数学' :
                       ev.subject === 'physics' ? '物理' :
                       ev.subject === 'chemistry' ? '化学' :
                       ev.subject === 'english' ? '英语' :
                       ev.relationType === 'EXAM' ? '考试' : '家庭'}
                    </span>
                    <span className={s.metaSep}>·</span>
                    <span>
                      {new Date(ev.startAt).toLocaleTimeString('zh-CN', {
                        hour: '2-digit',
                        minute: '2-digit',
                      })}
                    </span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* B4 · LegendBar */}
        <div
          className={s.legendBar}
          role="list"
          aria-label="事件图例"
          data-testid="p10-legend-bar"
        >
          <div
            className={s.legendItem}
            role="listitem"
            data-testid="p10-legend-bar-item-math"
          >
            <span
              className={s.legendDot}
              style={{ background: 'var(--tkn-subject-math, #C41E3A)' }}
              aria-hidden="true"
            />
            数学
          </div>
          <div
            className={s.legendItem}
            role="listitem"
            data-testid="p10-legend-bar-item-physics"
          >
            <span
              className={s.legendDot}
              style={{ background: 'var(--tkn-subject-physics, #0057B7)' }}
              aria-hidden="true"
            />
            物理
          </div>
          <div
            className={s.legendItem}
            role="listitem"
            data-testid="p10-legend-bar-item-chemistry"
          >
            <span
              className={s.legendDot}
              style={{ background: 'var(--tkn-subject-chemistry, #1A6B3A)' }}
              aria-hidden="true"
            />
            化学
          </div>
          <div
            className={s.legendItem}
            role="listitem"
            data-testid="p10-legend-bar-item-english"
          >
            <span
              className={s.legendDot}
              style={{ background: 'var(--tkn-subject-english, #9C4F00)' }}
              aria-hidden="true"
            />
            英语
          </div>
          <div
            className={s.legendItem}
            role="listitem"
            data-testid="p10-legend-bar-item-exam"
          >
            <span
              className={s.legendDot}
              style={{ background: '#FF3B30' }}
              aria-hidden="true"
            />
            考试
          </div>
          <div
            className={s.legendItem}
            role="listitem"
            data-testid="p10-legend-bar-item-family"
          >
            <span
              className={s.legendDot}
              style={{ background: '#FF9500' }}
              aria-hidden="true"
            />
            家庭
          </div>
        </div>
      </div>
    </div>
  );
};
