//
//  OnekeyLogin.m
//  网优助手
//
//  Created by 梁仲太 on 2020/6/22.
//

#import "BsdiffUpdate.h"

@interface BsdiffUpdate()

@property(nonatomic,copy)NSString *callbackId;
@property(nonatomic,assign)NSInteger bsdiffType;


@end

@implementation BsdiffUpdate

-(void)coolMethod:(CDVInvokedUrlCommand *)command {
    self.callbackId = command.callbackId;
    self.bsdiffType = [command.arguments[0] integerValue];
}

- (void)successWithMessage:(NSArray *)messages {
    if(self.callbackId==nil)return;
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsArray:messages];
    [result setKeepCallbackAsBool:NO];
    [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
}

- (void)faileWithMessage:(NSString *)message{
    if(self.callbackId==nil)return;
    CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:message];
    [self.commandDelegate sendPluginResult:result callbackId:self.callbackId];
}


@end
