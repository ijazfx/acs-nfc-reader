#import "AcsNfcReaderPlugin.h"
#import <acs_nfc_reader/acs_nfc_reader-Swift.h>

@implementation AcsNfcReaderPlugin
+ (void)registerWithRegistrar:(NSObject<FlutterPluginRegistrar>*)registrar {
  [SwiftAcsNfcReaderPlugin registerWithRegistrar:registrar];
}
@end
