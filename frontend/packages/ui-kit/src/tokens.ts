/**
 * Do not edit directly, this file was auto-generated.
 */

export const TknColorBlack = "#000000"; // Hero dark backgrounds, immersive product sections
export const TknColorWhite = "#ffffff"; // Text on dark backgrounds, button text on blue/dark CTAs
export const TknColorBgLight = "#f5f5f7"; // Alternate section backgrounds — slight blue-gray tint prevents sterility
export const TknColorBgDark = "#000000"; // Immersive dark section backgrounds
export const TknColorSurfaceDark1 = "#272729"; // Card backgrounds in dark sections
export const TknColorSurfaceDark2 = "#262628"; // Subtle surface variation in dark contexts
export const TknColorSurfaceDark3 = "#28282a"; // Elevated cards on dark backgrounds
export const TknColorSurfaceDark4 = "#2a2a2d"; // Highest dark surface elevation
export const TknColorSurfaceDark5 = "#242426"; // Deepest dark surface tone
export const TknColorTextPrimary = "#1d1d1f"; // Primary body text on light backgrounds
export const TknColorTextSecondary = "#000000cc"; // Secondary text, nav items on light. Effective ~#333 on white = 12.6:1
export const TknColorTextTertiary = "#0000007a"; // Disabled states, carousel controls. For decorative/large use only
export const TknColorTextOnDark = "#ffffff"; // Text on dark section backgrounds
export const TknColorButtonActive = "#ededf2"; // Active/pressed state for light buttons
export const TknColorButtonDefaultLight = "#fafafc"; // Search/filter button backgrounds
export const TknColorOverlayMedia = "#d2d2d7a3"; // Media control scrims, overlays
export const TknColorPrimaryDefault = "#0071e3"; // Apple Blue — ONLY chromatic accent in core palette. Primary CTA backgrounds, focus rings.
export const TknColorPrimaryDark = "#2997ff"; // Links/interactive on dark section backgrounds. Higher luminance for contrast on black.
export const TknColorPrimaryLink = "#0066cc"; // Inline text links on light backgrounds. Slightly darker than primary for text-level readability.
export const TknColorSuccessDefault = "#1a7d34"; // Success states, confirmations
export const TknColorWarningDefault = "#b45309"; // Warning states, caution banners
export const TknColorDangerDefault = "#c0392b"; // Error states, destructive actions
export const TknColorInfoDefault = "#005c99"; // Info banners, informational states
export const TknSubjectMath = "#c41e3a"; // EXCEPTION 1 — Carmine red. High-energy, distinct from physics/chemistry/english. For math subject chips, tags, left-bars.
export const TknSubjectPhysics = "#0057b7"; // EXCEPTION 1 — Cobalt blue. Physical world / science association.
export const TknSubjectChemistry = "#1a6b3a"; // EXCEPTION 1 — Forest green. Lab / organic chemistry association.
export const TknSubjectEnglish = "#9c4f00"; // EXCEPTION 1 — Amber / copper. Warm language / literary association.
export const TknMotionEaseAppleStandard =
  "cubic-bezier(0.25, 0.46, 0.45, 0.94)"; // Apple HIG standard — ease-out feel. Natural deceleration into resting state.
export const TknMotionEaseEnter = "cubic-bezier(0.0, 0.0, 0.2, 1.0)"; // Elements entering viewport. Fast start, smooth arrival.
export const TknMotionEaseExit = "cubic-bezier(0.4, 0.0, 1.0, 1.0)"; // Elements exiting. Immediate start, clean departure. Always faster than enter.
export const TknMotionEaseLinear = "cubic-bezier(0.0, 0.0, 1.0, 1.0)"; // Linear — use only for opacity fades where easing feels unnatural. Not for transforms.
export const TknMotionDurationFast = "150ms"; // Micro-interactions: hover state, focus ring appearance, button press feedback. Must be imperceptible.
export const TknMotionDurationBase = "250ms"; // Standard element transitions: cards expanding, content loading, state changes within view.
export const TknMotionDurationSlow = "400ms"; // Complex transitions: modal open, bottom sheet slide-up, page-level element reveals.
export const TknMotionDurationExitFast = "90ms"; // Exit paired with fast — 60% of 150ms. Hover-out, focus-blur.
export const TknMotionDurationExitBase = "150ms"; // Exit paired with base — 60% of 250ms. Standard exit transition.
export const TknMotionDurationExitSlow = "240ms"; // Exit paired with slow — 60% of 400ms. Modal close, sheet dismiss.
export const TknMotionStaggerListItem = "30ms"; // Stagger delay between list/grid items entering. 30-50ms range. Avoids 'all at once' or 'too slow' reveal.
export const TknMotionScalePress = "0.97"; // Scale on card/button tap press. Subtle physical feedback. Restore on release with ease-apple-standard.
export const TknRadiusXs = "5px"; // Small containers, link tags, small badges
export const TknRadiusSm = "8px"; // Buttons, product cards, image containers — the standard Apple button radius
export const TknRadiusMd = "11px"; // Search inputs, filter buttons
export const TknRadiusLg = "12px"; // Feature panels, lifestyle image containers. Maximum for rectangular elements.
export const TknRadiusPill = "980px"; // Full pill CTAs — 'Learn more', 'Shop' links. The signature Apple CTA link shape. 980px mirrors Apple's max-content-width.
export const TknRadiusCircle = "50%"; // Media controls (play/pause arrows), avatar images, circular icon buttons
export const TknShadowCard = "rgba(0,0,0,0.22) 3px 5px 30px 0px"; // Soft, diffused elevation for product cards. Mimics studio light on a physical object. Applied sparingly — most elements have NO shadow.
export const TknShadowNavGlass = "backdrop-filter: saturate(180%) blur(20px)"; // Not a box-shadow — this is the CSS backdrop-filter applied to rgba(0,0,0,0.80) nav background. Creates the Apple translucent glass navbar. Falls back to solid #1c1c1e in WeChat miniprogram (no backdrop-filter support).
export const TknShadowFocus = "0 0 0 2px #0071e3"; // Focus ring for keyboard navigation. Applied to ALL interactive elements without exception.
export const TknShadowNone = "none"; // Explicit no-shadow. Apple's default — most elements have no shadow at all.
export const TknSpacing1 = "2px"; // 0.5 unit — icon micro-alignment
export const TknSpacing2 = "4px"; // 0.5 base — dense internal padding, icon-to-text gap
export const TknSpacing3 = "6px"; // 0.75 unit — compact chip padding
export const TknSpacing4 = "8px"; // 1 base unit — component internal gap, button vertical padding
export const TknSpacing5 = "10px"; // 1.25 unit — fine-grained layout adjustment
export const TknSpacing6 = "12px"; // 1.5 unit — card internal padding
export const TknSpacing7 = "14px"; // 1.75 unit — nav link padding
export const TknSpacing8 = "16px"; // 2 base units — standard card padding, content inset
export const TknSpacing9 = "20px"; // 2.5 units — section sub-gap
export const TknSpacing10 = "24px"; // 3 base units — component separation
export const TknSpacing12 = "32px"; // 4 base units — section padding, major spacing
export const TknSpacing14 = "48px"; // 6 units — major section separation
export const TknSpacing16 = "64px"; // 8 units — hero vertical rhythm
export const TknSpacing20 = "80px"; // 10 units — generous hero padding
export const TknSpacing24 = "96px"; // 12 units — between major page sections
export const TknSpacing2xs = "2px"; // Alias: icon micro-alignment
export const TknSpacingXs = "4px"; // Alias: dense internal padding
export const TknSpacingSm = "8px"; // Alias: 1 base unit
export const TknSpacingMd = "16px"; // Alias: standard card padding
export const TknSpacingLg = "24px"; // Alias: component separation
export const TknSpacingXl = "32px"; // Alias: section padding
export const TknSpacing2xl = "48px"; // Alias: major section separation
export const TknSpacing3xl = "64px"; // Alias: hero vertical rhythm
export const TknSpacing4xl = "96px"; // Alias: between major page sections
export const TknFontDisplay =
  "'SF Pro Display', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif"; // Use at 20px and above. Optical sizing: wider letter spacing, thinner strokes for large sizes.
export const TknFontText =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif"; // Use at 19px and below. Tighter and sturdier for small sizes.
export const TknTypeDisplayHeroFontSize = "56px";
export const TknTypeDisplayHeroFontFamily =
  "'SF Pro Display', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeDisplayHeroFontWeight = 600;
export const TknTypeDisplayHeroLineHeight = 1.07;
export const TknTypeDisplayHeroLetterSpacing = "-0.28px";
export const TknTypeSectionHeadingFontSize = "40px";
export const TknTypeSectionHeadingFontFamily =
  "'SF Pro Display', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeSectionHeadingFontWeight = 600;
export const TknTypeSectionHeadingLineHeight = 1.1;
export const TknTypeSectionHeadingLetterSpacing = "0px";
export const TknTypeTileHeadingFontSize = "28px";
export const TknTypeTileHeadingFontFamily =
  "'SF Pro Display', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeTileHeadingFontWeight = 400;
export const TknTypeTileHeadingLineHeight = 1.14;
export const TknTypeTileHeadingLetterSpacing = "0.196px";
export const TknTypeCardTitleFontSize = "21px";
export const TknTypeCardTitleFontFamily =
  "'SF Pro Display', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeCardTitleFontWeight = 700;
export const TknTypeCardTitleLineHeight = 1.19;
export const TknTypeCardTitleLetterSpacing = "0.231px";
export const TknTypeSubHeadingFontSize = "21px";
export const TknTypeSubHeadingFontFamily =
  "'SF Pro Display', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeSubHeadingFontWeight = 400;
export const TknTypeSubHeadingLineHeight = 1.19;
export const TknTypeSubHeadingLetterSpacing = "0.231px";
export const TknTypeNavHeadingFontSize = "34px";
export const TknTypeNavHeadingFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeNavHeadingFontWeight = 600;
export const TknTypeNavHeadingLineHeight = 1.47;
export const TknTypeNavHeadingLetterSpacing = "-0.374px";
export const TknTypeBodyFontSize = "17px";
export const TknTypeBodyFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeBodyFontWeight = 400;
export const TknTypeBodyLineHeight = 1.47;
export const TknTypeBodyLetterSpacing = "-0.374px";
export const TknTypeBodyEmphasisFontSize = "17px";
export const TknTypeBodyEmphasisFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeBodyEmphasisFontWeight = 600;
export const TknTypeBodyEmphasisLineHeight = 1.24;
export const TknTypeBodyEmphasisLetterSpacing = "-0.374px";
export const TknTypeButtonLgFontSize = "18px";
export const TknTypeButtonLgFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeButtonLgFontWeight = 300;
export const TknTypeButtonLgLineHeight = 1;
export const TknTypeButtonLgLetterSpacing = "0px";
export const TknTypeButtonFontSize = "17px";
export const TknTypeButtonFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeButtonFontWeight = 400;
export const TknTypeButtonLineHeight = 2.41;
export const TknTypeButtonLetterSpacing = "0px";
export const TknTypeLinkFontSize = "14px";
export const TknTypeLinkFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeLinkFontWeight = 400;
export const TknTypeLinkLineHeight = 1.43;
export const TknTypeLinkLetterSpacing = "-0.224px";
export const TknTypeCaptionFontSize = "14px";
export const TknTypeCaptionFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeCaptionFontWeight = 400;
export const TknTypeCaptionLineHeight = 1.29;
export const TknTypeCaptionLetterSpacing = "-0.224px";
export const TknTypeCaptionBoldFontSize = "14px";
export const TknTypeCaptionBoldFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeCaptionBoldFontWeight = 600;
export const TknTypeCaptionBoldLineHeight = 1.29;
export const TknTypeCaptionBoldLetterSpacing = "-0.224px";
export const TknTypeMicroFontSize = "12px";
export const TknTypeMicroFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeMicroFontWeight = 400;
export const TknTypeMicroLineHeight = 1.33;
export const TknTypeMicroLetterSpacing = "-0.12px";
export const TknTypeNanoFontSize = "10px";
export const TknTypeNanoFontFamily =
  "'SF Pro Text', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif";
export const TknTypeNanoFontWeight = 400;
export const TknTypeNanoLineHeight = 1.47;
export const TknTypeNanoLetterSpacing = "-0.08px";
