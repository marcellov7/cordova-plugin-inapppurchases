#import "PaymentsPlugin.h"
#import "StoreKit2Manager.h"

@interface PaymentsPlugin() <SKPaymentTransactionObserver>
@end

@implementation PaymentsPlugin

- (void)pluginInitialize {
    [[StoreKit2Manager sharedInstance] addTransactionObserver:self];
}

- (void)dealloc {
    [[StoreKit2Manager sharedInstance] removeTransactionObserver:self];
}

#pragma mark - Public Methods

- (void)billingGetAllProductInfo:(CDVInvokedUrlCommand *)command {
    id productIds = [command.arguments objectAtIndex:0];
    if (![productIds isKindOfClass:[NSArray class]]) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR 
                                                        messageAsString:@"ProductIds must be an array of strings"];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    NSSet *products = [NSSet setWithArray:productIds];
    [[StoreKit2Manager sharedInstance] requestProductsWithIdentifiers:products 
                                                            success:^(NSArray *products, NSArray *invalidProductIdentifiers) {
        NSMutableDictionary *result = [NSMutableDictionary dictionary];
        NSMutableArray *validProducts = [NSMutableArray array];
        
        for (SKProduct *product in products) {
            NSString *country = [product.priceLocale objectForKey:NSLocaleCountryCode];
            NSString *currencyCode = [product.priceLocale objectForKey:NSLocaleCurrencyCode];
            
            NSNumber *isIntroductoryPriceSupported = @0;
            NSDictionary *introductoryPriceInfo = nil;
            
            if (@available(iOS 11.2, *)) {
                isIntroductoryPriceSupported = @1;
                if (product.introductoryPrice) {
                    SKProductDiscount *ip = product.introductoryPrice;
                    NSLocale *ipPriceLocale = ip.priceLocale ?: product.priceLocale;
                    NSNumberFormatter *formatter = [[NSNumberFormatter alloc] init];
                    formatter.numberStyle = NSNumberFormatterCurrencyStyle;
                    formatter.locale = ipPriceLocale;
                    
                    introductoryPriceInfo = @{
                        @"price": [formatter stringFromNumber:ip.price],
                        @"priceRaw": [ip.price stringValue],
                        @"country": [ipPriceLocale objectForKey:NSLocaleCountryCode],
                        @"currency": [ipPriceLocale objectForKey:NSLocaleCurrencyCode],
                        @"paymentMode": @(ip.paymentMode),
                        @"numberOfPeriods": @(ip.numberOfPeriods),
                        @"subscriptionPeriod": @{
                            @"unit": @(ip.subscriptionPeriod.unit),
                            @"numberOfUnits": @(ip.subscriptionPeriod.numberOfUnits),
                        }
                    };
                }
            }
            
            NSNumberFormatter *priceFormatter = [[NSNumberFormatter alloc] init];
            priceFormatter.numberStyle = NSNumberFormatterCurrencyStyle;
            priceFormatter.locale = product.priceLocale;
            
            [validProducts addObject:@{
                @"productId": product.productIdentifier,
                @"title": product.localizedTitle,
                @"description": product.localizedDescription,
                @"price": [priceFormatter stringFromNumber:product.price],
                @"priceAsDecimal": product.price,
                @"priceRaw": [product.price stringValue],
                @"country": country,
                @"currency": currencyCode,
                @"introductoryPrice": introductoryPriceInfo ?: [NSNull null],
                @"introductoryPriceSupported": isIntroductoryPriceSupported
            }];
        }
        
        [result setObject:validProducts forKey:@"products"];
        [result setObject:invalidProductIdentifiers forKey:@"invalidProductsIds"];
        
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK 
                                                      messageAsDictionary:result];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } failure:^(NSError *error) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR 
                                                      messageAsDictionary:@{
            @"code": @(error.code),
            @"message": error.localizedDescription
        }];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)billingPurchase:(CDVInvokedUrlCommand *)command {
    id productId = [command.arguments objectAtIndex:0];
    if (![productId isKindOfClass:[NSString class]]) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR 
                                                        messageAsString:@"ProductId must be a string"];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
        return;
    }
    
    [[StoreKit2Manager sharedInstance] purchaseProduct:productId 
                                             success:^(SKPaymentTransaction *transaction, NSString *receipt) {
        NSNumber *pending = @(transaction.transactionState != SKPaymentTransactionStatePurchased && 
                            transaction.transactionState != SKPaymentTransactionStateRestored);
        
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK 
                                                      messageAsDictionary:@{
            @"receipt": receipt ?: [NSNull null],
            @"productId": transaction.payment.productIdentifier,
            @"purchaseId": transaction.transactionIdentifier ?: [NSNull null],
            @"purchaseTime": @((NSInteger)transaction.transactionDate.timeIntervalSince1970),
            @"pending": pending,
            @"quantity": @(transaction.payment.quantity),
            @"verified": @0,
            @"completed": @(pending.intValue != 1)
        }];
        
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } failure:^(NSError *error) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR 
                                                      messageAsDictionary:@{
            @"code": @(error.code),
            @"message": error.localizedDescription
        }];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)billingRestorePurchases:(CDVInvokedUrlCommand *)command {
    [[StoreKit2Manager sharedInstance] restorePurchases:^(NSArray *transactions) {
        NSMutableArray *validTransactions = [NSMutableArray array];
        NSMutableDictionary *result = [NSMutableDictionary dictionary];
        
        for (SKPaymentTransaction *transaction in transactions) {
            if (transaction.transactionState != SKPaymentTransactionStateFailed) {
                NSNumber *pending = @(transaction.transactionState != SKPaymentTransactionStatePurchased && 
                                   transaction.transactionState != SKPaymentTransactionStateRestored);
                
                [validTransactions addObject:@{
                    @"productId": transaction.payment.productIdentifier,
                    @"purchaseId": transaction.transactionIdentifier ?: [NSNull null],
                    @"purchaseTime": @((NSInteger)transaction.transactionDate.timeIntervalSince1970),
                    @"pending": pending,
                    @"quantity": @(transaction.payment.quantity),
                    @"verified": @0,
                    @"completed": @(pending.intValue != 1)
                }];
            }
        }
        
        [result setObject:validTransactions forKey:@"transactions"];
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK 
                                                      messageAsDictionary:result];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    } failure:^(NSError *error) {
        CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR 
                                                      messageAsDictionary:@{
            @"code": @(error.code),
            @"message": error.localizedDescription
        }];
        [pluginResult setKeepCallbackAsBool:YES];
        [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
    }];
}

- (void)billingGetReceipt:(CDVInvokedUrlCommand *)command {
    NSString *receipt = [[StoreKit2Manager sharedInstance] getReceipt];
    CDVPluginResult *pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK 
                                                  messageAsDictionary:@{
        @"receipt": receipt ?: [NSNull null]
    }];
    [pluginResult setKeepCallbackAsBool:YES];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

#pragma mark - SKPaymentTransactionObserver

- (void)paymentQueue:(SKPaymentQueue *)queue updatedTransactions:(NSArray<SKPaymentTransaction *> *)transactions {
    for (SKPaymentTransaction *transaction in transactions) {
        if (transaction.transactionState == SKPaymentTransactionStatePurchased ||
            transaction.transactionState == SKPaymentTransactionStateRestored) {
            
            [[StoreKit2Manager sharedInstance] verifyPurchase:transaction 
                                                     success:^(BOOL verified) {
                if (verified) {
                    NSString *js = [NSString stringWithFormat:@"cordova.fireDocumentEvent('transactionfinished', %@);",
                                  @{@"productId": transaction.payment.productIdentifier,
                                    @"transactionId": transaction.transactionIdentifier ?: [NSNull null],
                                    @"receipt": [[StoreKit2Manager sharedInstance] getReceipt] ?: @""}];
                    [self.commandDelegate evalJs:js];
                }
            } failure:^(NSError *error) {
                NSLog(@"Purchase verification failed: %@", error);
            }];
        }
    }
}

@end
