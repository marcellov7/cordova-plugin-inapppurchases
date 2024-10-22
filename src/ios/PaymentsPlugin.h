#import <Foundation/Foundation.h>
#import <Cordova/CDVPlugin.h>
#import <StoreKit/StoreKit.h>

@interface PaymentsPlugin : CDVPlugin

// Main public methods (existing API)
- (void)billingGetAllProductInfo:(CDVInvokedUrlCommand *)command;
- (void)billingGetPurchases:(CDVInvokedUrlCommand *)command;
- (void)billingRestorePurchases:(CDVInvokedUrlCommand *)command;
- (void)billingPurchase:(CDVInvokedUrlCommand *)command;
- (void)billingGetReceipt:(CDVInvokedUrlCommand *)command;

@end
