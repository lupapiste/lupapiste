/*
 * Symbols used from outside cljs code need to be defined as externs when 'advanced' Closure compilation level is used.
 * This prevents listed symbols to be munged by the Google Closure compiler.
 * https://github.com/clojure/clojurescript/wiki/Compiler-Options#externs
 *
 * You can try out the advanced compilation locally (remember to stop figwheel first)
 *
 * lein with-profiles uberjar cljsbuild once
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
util.partyFullName = function() {};
util.finnishDateAndTime = function() {};
util.finnishDate = function() {};
util.toMoment = function() {};
util.prop = {};
util.prop.toHumanFormat = function() {};

var sprintf = function() {};

var features = {};
features.enabled = function() {};

var LUPAPISTE = {};
LUPAPISTE.FileuploadService = function() {};

var hub = {};
hub.send = function() {};
hub.subscribe = function() {};
hub.unsubscribe = function() {};
hub.onPageLoad = function() {};
hub.onPageUnload = function() {};

var loc = function() {};
loc.getCurrentLanguage = function() {};
loc.currentLanguage = function() {};
loc.setTerms = function() {};
loc.hasTerm = function() {};
loc.getErrorMessages = function() {};
loc.getSupportedLanguages = function() {};
loc.getNameByCurrentLanguage = function() {};
loc.supported = [];
loc.terms = {};
loc.defaultLanguage = "fi";

var pageutil = {};
pageutil.openPage = function() {};

var lupapisteApp = {};
lupapisteApp.services = {};
lupapisteApp.services.fileUploadService = {};
lupapisteApp.services.fileUploadService.bindFileInput = function() {};
lupapisteApp.services.attachmentsService = {};
lupapisteApp.services.attachmentsService.bindAttachments = function() {};
lupapisteApp.services.attachmentsService.removeAttachment = function() {};
lupapisteApp.services.commentService = {};
lupapisteApp.services.organizationTagsService = {};
lupapisteApp.services.applicationFiltersService = {};
lupapisteApp.services.areaFilterService = {};
lupapisteApp.services.handlerFilterService = {};
lupapisteApp.services.tagFilterService = {};
lupapisteApp.services.organizationFilterService = {};
lupapisteApp.services.operationFilterService = {};
lupapisteApp.services.publishBulletinService = {};
lupapisteApp.services.documentDataService = {};
lupapisteApp.services.sidePanelService = {};
lupapisteApp.services.accordionService = {};
lupapisteApp.services.accordionService.attachmentAccordionName = function() {};
lupapisteApp.services.verdictAppealService = {};
lupapisteApp.services.scrollService = {};
lupapisteApp.services.ramService = {};
lupapisteApp.services.calendarService = {};
lupapisteApp.services.sutiService = {};
lupapisteApp.services.infoService = {};
lupapisteApp.services.contextService = {};
lupapisteApp.services.contextService.applicationId = function() {};
lupapisteApp.services.buildingService = {};
lupapisteApp.services.assignmentService = {};
lupapisteApp.services.assignmentRecipientFilterService = {};
lupapisteApp.services.assignmentTargetFilterService = {};
lupapisteApp.services.eventFilterService = {};
lupapisteApp.services.inspectionSummaryService = {};
lupapisteApp.services.handlerService = {};
lupapisteApp.services.cardService = {};

lupapisteApp.models = {};
lupapisteApp.models.application = {};
lupapisteApp.models.application.userHasRole = function() {};
lupapisteApp.models.currentUser = {};
lupapisteApp.models.currentUser.firstName = function() {};
lupapisteApp.models.currentUser.lastName = function() {};
lupapisteApp.models.currentUser.displayName = function() {};
lupapisteApp.models.currentUser.street = function() {};
lupapisteApp.models.currentUser.zip = function() {};
lupapisteApp.models.currentUser.city = function() {};
lupapisteApp.models.currentUser.email = function() {};
lupapisteApp.models.currentUser.company = {};
lupapisteApp.models.currentUser.company.id = function() {};
lupapisteApp.models.applicationAuthModel = {};
lupapisteApp.models.applicationAuthModel.ok = function() {};
lupapisteApp.models.globalAuthModel = {};
lupapisteApp.models.globalAuthModel.ok = function() {};
