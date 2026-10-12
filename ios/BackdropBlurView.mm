#import "BackdropBlurView.h"

#import <react/renderer/components/BackdropBlurViewSpec/ComponentDescriptors.h>
#import <react/renderer/components/BackdropBlurViewSpec/Props.h>
#import <react/renderer/components/BackdropBlurViewSpec/RCTComponentViewHelpers.h>

#import "RCTFabricComponentsPlugins.h"

using namespace facebook::react;

// The system material (UIVisualEffectView) already blurs whatever is behind
// it in real time, which is exactly CSS backdrop-filter. It has no public
// "blur radius", so strength is controlled the usual public-API way: put
// "effect goes from nil to blur" in a UIViewPropertyAnimator and stop it part
// way (fractionComplete), so the blur and the material's own tint only apply
// proportionally.
//
// SystemUltraThinMaterialDark is the material with the lightest built-in
// tint; colour is left to whatever tint layer the caller puts on top, to stay
// close to "plain blur + a tint layer" designs.
//
// At full intensity the material blurs at roughly 30pt (Apple doesn't publish
// the number; this was eyeballed against CSS blur). blurRadius is mapped to a
// fraction against it.
static const CGFloat kSystemBlurRadiusAtFullIntensity = 30.0;

@interface BackdropBlurView () <RCTBackdropBlurViewViewProtocol>
@end

@implementation BackdropBlurView {
  UIVisualEffectView *_effectView;
  UIViewPropertyAnimator *_animator;
  CGFloat _blurRadius;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider
{
  return concreteComponentDescriptorProvider<BackdropBlurViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame
{
  if (self = [super initWithFrame:frame]) {
    static const auto defaultProps = std::make_shared<const BackdropBlurViewProps>();
    _props = defaultProps;

    _blurRadius = defaultProps->blurRadius;
    _effectView = [[UIVisualEffectView alloc] initWithEffect:nil];
    _effectView.frame = self.bounds;
    _effectView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    _effectView.userInteractionEnabled = NO;
    [self addSubview:_effectView];
    self.clipsToBounds = YES;
    self.userInteractionEnabled = NO;

    // After a background/foreground round trip, a paused animator gets reset
    // to full intensity or stops working; rebuild it on becoming active.
    [[NSNotificationCenter defaultCenter] addObserver:self
                                             selector:@selector(applicationDidBecomeActive:)
                                                 name:UIApplicationDidBecomeActiveNotification
                                               object:nil];
  }
  return self;
}

- (void)dealloc
{
  [[NSNotificationCenter defaultCenter] removeObserver:self];
  [self teardownBlur];
}

- (void)updateProps:(Props::Shared const &)props oldProps:(Props::Shared const &)oldProps
{
  const auto &newViewProps = *std::static_pointer_cast<const BackdropBlurViewProps>(props);
  if (newViewProps.blurRadius != _blurRadius) {
    _blurRadius = newViewProps.blurRadius;
    [self setupBlurIfNeeded];
  }
  [super updateProps:props oldProps:oldProps];
}

- (void)layoutSubviews
{
  [super layoutSubviews];
  _effectView.frame = self.bounds;
}

- (void)didMoveToWindow
{
  [super didMoveToWindow];
  [self setupBlurIfNeeded];
}

- (void)prepareForRecycle
{
  [super prepareForRecycle];
  [self teardownBlur];
}

- (void)applicationDidBecomeActive:(__unused NSNotification *)notification
{
  [self setupBlurIfNeeded];
}

- (void)setupBlurIfNeeded
{
  [self teardownBlur];
  if (self.window == nil) {
    return;
  }

  CGFloat fraction = MAX(0.0, MIN(1.0, _blurRadius / kSystemBlurRadiusAtFullIntensity));
  if (fraction >= 1.0) {
    // Full intensity needs no animator; just apply the material.
    _effectView.effect = [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemUltraThinMaterialDark];
    return;
  }

  UIBlurEffect *effect = [UIBlurEffect effectWithStyle:UIBlurEffectStyleSystemUltraThinMaterialDark];
  __weak UIVisualEffectView *effectView = _effectView;
  _animator = [[UIViewPropertyAnimator alloc] initWithDuration:1.0
                                                         curve:UIViewAnimationCurveLinear
                                                    animations:^{
                                                      effectView.effect = effect;
                                                    }];
  _animator.pausesOnCompletion = YES;
  _animator.fractionComplete = fraction;
}

- (void)teardownBlur
{
  if (_animator != nil) {
    // stopAnimation:YES moves the animator straight to inactive, after which
    // releasing it is safe; releasing one left active/paused crashes.
    if (_animator.state == UIViewAnimatingStateActive) {
      [_animator stopAnimation:YES];
    }
    _animator = nil;
  }
  _effectView.effect = nil;
}

@end

Class<RCTComponentViewProtocol> BackdropBlurViewCls(void)
{
  return BackdropBlurView.class;
}
