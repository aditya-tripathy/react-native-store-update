#import "InAppUpdate.h"

#import <UIKit/UIKit.h>

static NSString *const kLookupEndpoint = @"https://itunes.apple.com/lookup";
static NSString *const kErrorCheckFailed = @"E_UPDATE_CHECK_FAILED";
static NSString *const kErrorAppNotFound = @"E_APP_NOT_FOUND";
static NSString *const kErrorNoStoreURL = @"E_NO_STORE_URL";
static NSString *const kErrorOpenStoreFailed = @"E_OPEN_STORE_FAILED";

@interface InAppUpdate ()
// Written from the lookup callback and read from the main queue in startUpdate.
@property (atomic, copy, nullable) NSString *appStoreURL;
@end

@implementation InAppUpdate

RCT_EXPORT_MODULE()

+ (BOOL)requiresMainQueueSetup
{
  return NO;
}

// iOS has no equivalent of Play's in-app update flow, so the App Store listing is
// looked up and the installed version compared against the published one.
RCT_EXPORT_METHOD(checkForUpdate:(NSString *)country
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  NSString *bundleId = NSBundle.mainBundle.bundleIdentifier;
  NSString *currentVersion = [NSBundle.mainBundle objectForInfoDictionaryKey:@"CFBundleShortVersionString"];
  if (![currentVersion isKindOfClass:NSString.class]) {
    currentVersion = @"";
  }
  if (bundleId.length == 0) {
    reject(kErrorCheckFailed, @"The app has no bundle identifier to look up.", nil);
    return;
  }

  NSURLComponents *components = [NSURLComponents componentsWithString:kLookupEndpoint];
  NSMutableArray<NSURLQueryItem *> *queryItems = [NSMutableArray arrayWithObject:
      [NSURLQueryItem queryItemWithName:@"bundleId" value:bundleId]];
  NSString *storefront = [self normalizedCountryCode:country];
  if (storefront != nil) {
    [queryItems addObject:[NSURLQueryItem queryItemWithName:@"country" value:storefront]];
  }
  components.queryItems = queryItems;
  NSURL *url = components.URL;
  if (url == nil) {
    reject(kErrorCheckFailed, @"Could not build the App Store lookup URL.", nil);
    return;
  }

  NSMutableURLRequest *request = [NSMutableURLRequest requestWithURL:url];
  request.cachePolicy = NSURLRequestReloadIgnoringLocalAndRemoteCacheData;
  request.timeoutInterval = 15;

  __weak __typeof(self) weakSelf = self;
  NSURLSessionDataTask *task = [NSURLSession.sharedSession
      dataTaskWithRequest:request
        completionHandler:^(NSData *_Nullable data, NSURLResponse *_Nullable response, NSError *_Nullable error) {
          if (error != nil) {
            reject(kErrorCheckFailed, error.localizedDescription, error);
            return;
          }
          if ([response isKindOfClass:NSHTTPURLResponse.class]) {
            NSInteger statusCode = ((NSHTTPURLResponse *)response).statusCode;
            if (statusCode < 200 || statusCode > 299) {
              reject(kErrorCheckFailed,
                     [NSString stringWithFormat:@"The App Store lookup failed with HTTP %ld.", (long)statusCode],
                     nil);
              return;
            }
          }

          id json = data.length > 0 ? [NSJSONSerialization JSONObjectWithData:data options:0 error:nil] : nil;
          if (![json isKindOfClass:NSDictionary.class]) {
            reject(kErrorCheckFailed, @"The App Store lookup returned an unexpected response.", nil);
            return;
          }
          id results = ((NSDictionary *)json)[@"results"];
          if (![results isKindOfClass:NSArray.class] || ((NSArray *)results).count == 0) {
            reject(kErrorAppNotFound,
                   @"No App Store listing was found for this bundle identifier. "
                   @"Pass the storefront country to checkForUpdate() if the app is not on the US store.",
                   nil);
            return;
          }
          id entry = ((NSArray *)results).firstObject;
          if (![entry isKindOfClass:NSDictionary.class]) {
            reject(kErrorCheckFailed, @"The App Store lookup returned an unexpected response.", nil);
            return;
          }

          id rawStoreVersion = ((NSDictionary *)entry)[@"version"];
          NSString *storeVersion = [rawStoreVersion isKindOfClass:NSString.class] ? rawStoreVersion : @"";
          id rawTrackViewURL = ((NSDictionary *)entry)[@"trackViewUrl"];
          if ([rawTrackViewURL isKindOfClass:NSString.class]) {
            weakSelf.appStoreURL = rawTrackViewURL;
          }

          BOOL updateAvailable = storeVersion.length > 0 && currentVersion.length > 0 &&
              [InAppUpdate isVersion:storeVersion newerThan:currentVersion];

          resolve(@{
            @"updateAvailable" : @(updateAvailable),
            @"currentVersion" : currentVersion,
            @"storeVersion" : storeVersion,
            // Version codes, priorities and Play update flows do not exist on iOS.
            @"currentVersionCode" : @(-1),
            @"availableVersionCode" : @(-1),
            @"updatePriority" : @(-1),
            @"clientVersionStalenessDays" : @(-1),
            @"isFlexibleUpdateAllowed" : @(NO),
            @"isImmediateUpdateAllowed" : @(NO),
            @"isUpdateInProgress" : @(NO),
            @"installStatus" : @"unknown",
          });
        }];
  [task resume];
}

// The update type is meaningless on iOS: all we can do is send the user to the store.
RCT_EXPORT_METHOD(startUpdate:(NSString *)updateType
                  resolve:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  NSString *storeURLString = self.appStoreURL;
  NSURL *storeURL = storeURLString.length > 0 ? [NSURL URLWithString:storeURLString] : nil;
  if (storeURL == nil) {
    reject(kErrorNoStoreURL,
           @"The App Store URL is unknown. Call checkForUpdate() before startUpdate().",
           nil);
    return;
  }

  dispatch_async(dispatch_get_main_queue(), ^{
    [UIApplication.sharedApplication openURL:storeURL
                                     options:@{}
                           completionHandler:^(BOOL success) {
                             if (success) {
                               resolve(@"openedAppStore");
                             } else {
                               reject(kErrorOpenStoreFailed, @"Could not open the App Store page.", nil);
                             }
                           }];
  });
}

// Flexible updates are Android-only; nothing to install here.
RCT_EXPORT_METHOD(completeUpdate:(RCTPromiseResolveBlock)resolve
                  reject:(RCTPromiseRejectBlock)reject)
{
  resolve(nil);
}

// This module never emits events on iOS; both methods exist for the NativeEventEmitter contract.
RCT_EXPORT_METHOD(addListener:(NSString *)eventName) {}

RCT_EXPORT_METHOD(removeListeners:(double)count) {}

#pragma mark - Helpers

// Only an ISO 3166-1 alpha-2 storefront is accepted; anything else is dropped.
- (nullable NSString *)normalizedCountryCode:(NSString *)country
{
  if (![country isKindOfClass:NSString.class] || country.length != 2) {
    return nil;
  }
  NSCharacterSet *letters = [NSCharacterSet characterSetWithCharactersInString:@"ABCDEFGHIJKLMNOPQRSTUVWXYZ"];
  NSString *uppercased = country.uppercaseString;
  for (NSUInteger i = 0; i < uppercased.length; i++) {
    if (![letters characterIsMember:[uppercased characterAtIndex:i]]) {
      return nil;
    }
  }
  return uppercased.lowercaseString;
}

// Compares dot-separated version strings numerically; non-numeric suffixes are ignored.
+ (BOOL)isVersion:(NSString *)candidate newerThan:(NSString *)current
{
  NSArray<NSString *> *candidateParts = [candidate componentsSeparatedByString:@"."];
  NSArray<NSString *> *currentParts = [current componentsSeparatedByString:@"."];
  NSUInteger count = MAX(candidateParts.count, currentParts.count);
  for (NSUInteger i = 0; i < count; i++) {
    NSInteger left = i < candidateParts.count ? candidateParts[i].integerValue : 0;
    NSInteger right = i < currentParts.count ? currentParts[i].integerValue : 0;
    if (left != right) {
      return left > right;
    }
  }
  return NO;
}

#ifdef RCT_NEW_ARCH_ENABLED
- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
  return std::make_shared<facebook::react::NativeInAppUpdateSpecJSI>(params);
}
#endif

@end
