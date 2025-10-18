/**
 * 🧩 commonPagination.js
 * --------------------------------------------------------
 * ✅ 공용 페이징 처리 JS
 * --------------------------------------------------------
 *
 * 사용법:
 *   initPagination({
 *     paginationSelector: "#pagination",
 *     tableBodySelector: "#dataTable",
 *     currentPage: 0,
 *     totalPages: 10,
 *     onPageChange: (page) => { ...fetchData(page)... }
 *   });
 *
 * 주요 기능:
 *   1. 이전/다음/첫/마지막 페이지 버튼
 *   2. 그룹 단위 페이지 표시 (반응형)
 *   3. 현재 페이지 표시
 *   4. 화면 크기 변경 시 그룹 사이즈 조정
 */

function initPagination(config) {
  const {
    paginationSelector,
    tableBodySelector,
    currentPage: initPage = 0,
    totalPages: totalPagesInit = 1,
    pageGroupSize: configGroupSize = 5,
    onPageChange
  } = config;

  const $ = sel => document.querySelector(sel);
  const $$ = sel => document.querySelectorAll(sel);

  let currentPage = initPage;
  let totalPages = totalPagesInit;
  let groupSize = configGroupSize;

  // ===============================
  // 그룹 크기 자동 조정 (반응형)
  // ===============================
  function adjustGroupSize() {
    const tbody = $(tableBodySelector);
    if (!tbody) return;
    const containerWidth = tbody.offsetWidth;
    const approxBtnWidth = 36; // 버튼 예상 폭
    let maxBtnPerRow = Math.floor(containerWidth / approxBtnWidth);
    if (window.innerWidth <= 768) maxBtnPerRow = Math.min(maxBtnPerRow, 5);
    groupSize = Math.min(configGroupSize, maxBtnPerRow, totalPages);
    if (groupSize < 1) groupSize = 1;
  }

  // ===============================
  // 페이징 렌더링
  // ===============================
  function renderPagination() {
    adjustGroupSize();
    const container = $(paginationSelector);
    if (!container) return;

    container.innerHTML = "";
    if (totalPages <= 0) return;

    const currentGroup = Math.floor(currentPage / groupSize);
    const startPage = currentGroup * groupSize;
    const endPage = Math.min(startPage + groupSize, totalPages);

    const makeBtn = (text, disabled, click) => {
      const btn = document.createElement("button");
      btn.textContent = text;
      btn.disabled = disabled;
      if (!disabled && typeof click === "function") btn.addEventListener("click", click);
      container.appendChild(btn);
    };

    // ⏮️ 첫 페이지
    makeBtn("<<", currentPage === 0, () => changePage(0));
    // ◀️ 이전
    makeBtn("<", currentPage === 0, () => changePage(currentPage - 1));

    // 그룹 내 페이지
    for (let i = startPage; i < endPage; i++) {
      const btn = document.createElement("button");
      btn.textContent = i + 1;
      if (i === currentPage) btn.classList.add("active");
      btn.addEventListener("click", () => changePage(i));
      container.appendChild(btn);
    }

    // ▶️ 다음
    makeBtn(">", currentPage >= totalPages - 1, () => changePage(currentPage + 1));
    // ⏭️ 마지막
    makeBtn(">>", currentPage >= totalPages - 1, () => changePage(totalPages - 1));

    container.style.maxWidth = $(tableBodySelector)?.offsetWidth + "px";
  }

  // ===============================
  // 페이지 변경
  // ===============================
  function changePage(page) {
    if (page < 0) page = 0;
    if (page >= totalPages) page = totalPages - 1;
    currentPage = page;
    renderPagination();
    if (typeof onPageChange === "function") onPageChange(currentPage);
  }

  // ===============================
  // 외부에서 총 페이지 갱신
  // ===============================
  function updateTotalPages(total) {
    totalPages = total;
    if (currentPage >= totalPages) currentPage = totalPages - 1;
    if (currentPage < 0) currentPage = 0;
    renderPagination();
  }

  // ===============================
  // 화면 리사이즈 이벤트
  // ===============================
  window.addEventListener("resize", () => renderPagination());

  // ===============================
  // 초기 렌더링
  // ===============================
  renderPagination();

  // ===============================
  // 공개 API
  // ===============================
  return {
    goToPage: changePage,
    updateTotalPages
  };
}

