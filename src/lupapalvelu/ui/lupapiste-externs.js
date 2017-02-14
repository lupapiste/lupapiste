/*
 * Symbols used from outside cljs code need to be defined as externs when 'advanced' Closure compilation level is used.
 * This prevents listed symbols to be munged by the Google Closure compiler.
 * https://github.com/clojure/clojurescript/wiki/Compiler-Options#externs
 */
var ajax = {};

ajax.post = function () {};
ajax.postJson = function () {};
ajax.get = function () {};
ajax.deleteReq = function () {};
ajax.command = function () {};
ajax.query = function() {};
ajax.datatables = function () {};
ajax.form = function () {};

ajax.request = function() {};
ajax.customErrorHandlers = function() {};
ajax.errorHandler = function() {};
ajax.onComplete = function() {};
ajax.pendingListener = function() {};
ajax.pendingHandler = function() {};
ajax.processingListener = function() {};
ajax.completeHandler = function() {};
ajax.callId = function() {};
ajax.rawData = function() {};
ajax.successHandler = function() {};
ajax.savedThis = function() {};
ajax.fuseListener = function() {};
ajax.failHandler = function() {};
ajax.headers = function() {};
ajax.raw = function() {};
ajax.dataType = function() {};
ajax.param = function() {};
ajax.params = function() {};
ajax.json = function() {};
ajax.success = function() {};
ajax.successEvent = function() {};
ajax.onError = function() {};
ajax.error = function() {};
ajax.errorEvent = function() {};
ajax.fail = function() {};
ajax.failEvent = function() {};
ajax.complete = function() {};
ajax.completeEvent = function() {};
ajax.timeout = function() {};
ajax.fuse = function() {};
ajax.processing = function() {};
ajax.pending = function() {};
ajax.pendingTimeout = function() {};
ajax.call = function() {};
ajax.header = function() {};

var util = {};
util.showSavedIndicator = function() {};
