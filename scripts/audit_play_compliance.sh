#!/usr/bin/env bash
# ==============================================================================
# scripts/audit_play_compliance.sh
# Production Store Compliance & External Link Auditor for Antigravity Remote
# Uses local checks + Firecrawl CLI (Keyless or Authenticated)
# ==============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

METADATA_DIR="${REPO_ROOT}/fastlane/metadata/android/en-US"
PRIVACY_FILE="${REPO_ROOT}/PRIVACY.md"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

ERRORS=0
WARNINGS=0

echo -e "${BLUE}======================================================${NC}"
echo -e "${BLUE}   Google Play Store Compliance & Asset Audit (CI)    ${NC}"
echo -e "${BLUE}======================================================${NC}"

log_pass() { echo -e "  [${GREEN}PASS${NC}] $1"; }
log_fail() { echo -e "  [${RED}FAIL${NC}] $1"; ERRORS=$((ERRORS + 1)); }
log_warn() { echo -e "  [${YELLOW}WARN${NC}] $1"; WARNINGS=$((WARNINGS + 1)); }

# ------------------------------------------------------------------------------
# Phase 1: Fastlane Metadata Text & Length Verification
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}--- Phase 1: Store Listing Metadata Validation ---${NC}"

TITLE_FILE="${METADATA_DIR}/title.txt"
if [[ -f "${TITLE_FILE}" ]]; then
    TITLE=$(cat "${TITLE_FILE}" | tr -d '\r\n')
    TITLE_LEN=${#TITLE}
    if [[ ${TITLE_LEN} -le 30 ]]; then
        log_pass "Title length is ${TITLE_LEN}/30 chars: \"${TITLE}\""
    else
        log_fail "Title exceeds 30 chars (${TITLE_LEN}/30): \"${TITLE}\""
    fi

    if [[ "${TITLE}" =~ ^Google\  ]]; then
        log_fail "Title starts with 'Google ' which violates Google Play Impersonation Policy."
    else
        log_pass "Title brand naming complies with Android Brand Guidelines."
    fi
else
    log_fail "Missing ${TITLE_FILE}"
fi

SHORT_DESC_FILE="${METADATA_DIR}/short_description.txt"
if [[ -f "${SHORT_DESC_FILE}" ]]; then
    SHORT_DESC=$(cat "${SHORT_DESC_FILE}" | tr -d '\r\n')
    SHORT_DESC_LEN=${#SHORT_DESC}
    if [[ ${SHORT_DESC_LEN} -le 80 ]]; then
        log_pass "Short description length is ${SHORT_DESC_LEN}/80 chars."
    else
        log_fail "Short description exceeds 80 chars (${SHORT_DESC_LEN}/80)."
    fi
else
    log_fail "Missing ${SHORT_DESC_FILE}"
fi

FULL_DESC_FILE="${METADATA_DIR}/full_description.txt"
if [[ -f "${FULL_DESC_FILE}" ]]; then
    FULL_DESC=$(cat "${FULL_DESC_FILE}")
    FULL_DESC_LEN=${#FULL_DESC}
    if [[ ${FULL_DESC_LEN} -le 4000 ]]; then
        log_pass "Full description length is ${FULL_DESC_LEN}/4000 chars."
    else
        log_fail "Full description exceeds 4000 chars (${FULL_DESC_LEN}/4000)."
    fi

    if grep -iq "not affiliated with, sponsored by, or endorsed by Google" "${FULL_DESC_FILE}"; then
        log_pass "Mandatory non-affiliation disclaimer verified in full description."
    else
        log_fail "Missing non-affiliation disclaimer in full_description.txt."
    fi
else
    log_fail "Missing ${FULL_DESC_FILE}"
fi

# ------------------------------------------------------------------------------
# Phase 2: Graphic Asset Specifications (512x512 Icon & Size <= 1024KB)
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}--- Phase 2: Store Graphic Asset Verification ---${NC}"

ICON_PATH="${METADATA_DIR}/images/icon.png"
if [[ -f "${ICON_PATH}" ]]; then
    ICON_SIZE_BYTES=$(stat -c%s "${ICON_PATH}" 2>/dev/null || stat -f%z "${ICON_PATH}" 2>/dev/null)
    if [[ ${ICON_SIZE_BYTES} -le 1048576 ]]; then
        log_pass "Icon file size: ${ICON_SIZE_BYTES} bytes (<= 1,048,576 bytes / 1024 KB)."
    else
        log_fail "Icon file size ${ICON_SIZE_BYTES} bytes exceeds Google Play max limit of 1024 KB."
    fi

    if command -v file >/dev/null 2>&1; then
        FILE_OUTPUT=$(file "${ICON_PATH}")
        if [[ "${FILE_OUTPUT}" =~ 512\ x\ 512 ]]; then
            log_pass "Icon dimensions verified: 512x512 px."
        else
            log_fail "Icon dimensions are not 512x512 px (${FILE_OUTPUT})."
        fi
    fi
else
    log_fail "Store icon missing at ${ICON_PATH}"
fi

# ------------------------------------------------------------------------------
# Phase 3: Live Link Verification via Firecrawl Scrape
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}--- Phase 3: Live Link Verification via Firecrawl ---${NC}"

verify_url_firecrawl() {
    local target_url="$1"
    local description="$2"
    echo -e "  Checking ${description}: ${target_url} ..."

    if npx -y firecrawl-cli@latest scrape "${target_url}" -o /tmp/fc_verify.md >/dev/null 2>&1; then
        if grep -iq "404 Not Found\|Page Not Found\|This page could not be found" /tmp/fc_verify.md; then
            log_fail "${description} returned 404/Not Found content."
        else
            log_pass "${description} is active and readable."
        fi
    else
        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -L --max-time 10 "${target_url}" || echo "000")
        if [[ "${HTTP_CODE}" =~ ^(200|301|302|307|308)$ ]]; then
            log_pass "${description} reachable via HTTP fallback (code: ${HTTP_CODE})."
        else
            log_fail "${description} unreachable (HTTP ${HTTP_CODE})."
        fi
    fi
}

verify_url_firecrawl "https://support.google.com/googleplay/android-developer/answer/9888374" "Impersonation Policy"
verify_url_firecrawl "https://www.apache.org/licenses/LICENSE-2.0" "Apache 2.0 License"

# ------------------------------------------------------------------------------
# Phase 4: Policy & Privacy Disclosures Audit
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}--- Phase 4: Policy & Privacy Disclosures Audit ---${NC}"

if [[ -f "${PRIVACY_FILE}" ]]; then
    if grep -iq "Zero Third-Party Telemetry" "${PRIVACY_FILE}" && grep -iq "CookieManager" "${PRIVACY_FILE}"; then
        log_pass "PRIVACY.md contains required data collection and telemetry disclosures."
    else
        log_warn "PRIVACY.md should clearly articulate local cookie and zero-telemetry handling."
    fi

    if grep -iq "FOREGROUND_SERVICE" "${PRIVACY_FILE}"; then
        log_pass "PRIVACY.md contains justification for FOREGROUND_SERVICE permission."
    else
        log_fail "PRIVACY.md missing permission justification for foreground keep-alive service."
    fi
else
    log_fail "Missing PRIVACY.md"
fi

# ------------------------------------------------------------------------------
# Summary & Exit
# ------------------------------------------------------------------------------
echo -e "\n${BLUE}======================================================${NC}"
echo -e "Audit Finished: ${ERRORS} Error(s), ${WARNINGS} Warning(s)"
echo -e "${BLUE}======================================================${NC}"

if [[ ${ERRORS} -gt 0 ]]; then
    echo -e "${RED}Store Listing Compliance Audit FAILED.${NC}"
    exit 1
else
    echo -e "${GREEN}Store Listing Compliance Audit PASSED.${NC}"
    exit 0
fi
