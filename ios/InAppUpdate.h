#ifdef RCT_NEW_ARCH_ENABLED
#import <InAppUpdateSpec/InAppUpdateSpec.h>

@interface InAppUpdate : NSObject <NativeInAppUpdateSpec>
#else
#import <React/RCTBridgeModule.h>

@interface InAppUpdate : NSObject <RCTBridgeModule>
#endif

@end
