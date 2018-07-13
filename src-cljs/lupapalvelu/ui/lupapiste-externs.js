/*
 * Symbols used from outside cljs code need to be defined as externs when 'advanced' Closure compilation level is used.
 * This prevents listed symbols to be munged by the Google Closure compiler.
 * https://github.com/clojure/clojurescript/wiki/Compiler-Options#externs
 *
 * You can try out the advanced compilation locally (remember to stop figwheel first)
 *
 * lein with-profiles uberjar uberjar once
 *
 * A "lighter" alternative is
 *
 * lein with-profiles uberjar cljsbuild once
 *
 * but that does not find all the problematic issues.
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

var notify = {};
notify.ajaxError = function() {};

var util = {};
util.finnishDate        = function() {};
util.finnishDateAndTime = function() {};
util.partyFullName      = function() {};
util.prop = {};
util.prop.toHumanFormat = function() {};
util.showSavedIndicator = function() {};
util.sizeString         = function() {};
util.toMoment           = function() {};
util.localeComparator   = function() {};
util.getIn              = function() {};

var sprintf = function() {};

var features = {};
features.enabled = function() {};

var LUPAPISTE = {};
LUPAPISTE.FileuploadService = function() {};
LUPAPISTE.config = {};
LUPAPISTE.config.build = "";

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
pageutil.getPagePath = function() {};
pageutil.hashApplicationId = function() {};

var lupapisteApp = {};
lupapisteApp.services = {};
lupapisteApp.services.accordionService = {};
lupapisteApp.services.accordionService.attachmentAccordionName = function() {};
lupapisteApp.services.applicationFiltersService = {};
lupapisteApp.services.areaFilterService = {};
lupapisteApp.services.assignmentRecipientFilterService = {};
lupapisteApp.services.assignmentService = {};
lupapisteApp.services.assignmentTargetFilterService = {};
lupapisteApp.services.attachmentsService = {};
lupapisteApp.services.attachmentsService.attachmentTypes = function() {};
lupapisteApp.services.attachmentsService.bindAttachments = function() {};
lupapisteApp.services.attachmentsService.contentsData = function() {};
lupapisteApp.services.attachmentsService.getAuthModel = function() {};
lupapisteApp.services.attachmentsService.queryAll = function() {};
lupapisteApp.services.attachmentsService.rawAttachments = function() {};
lupapisteApp.services.attachmentsService.removeAttachment = function() {};
lupapisteApp.services.attachmentsService.refreshAuthModels = function() {};
lupapisteApp.services.buildingService = {};
lupapisteApp.services.calendarService = {};
lupapisteApp.services.cardService = {};
lupapisteApp.services.commentService = {};
lupapisteApp.services.contextService = {};
lupapisteApp.services.contextService.applicationId = function() {};
lupapisteApp.services.documentDataService = {};
lupapisteApp.services.eventFilterService = {};
lupapisteApp.services.fileUploadService = {};
lupapisteApp.services.fileUploadService.bindFileInput = function() {};
lupapisteApp.services.handlerFilterService = {};
lupapisteApp.services.handlerService = {};
lupapisteApp.services.infoService = {};
lupapisteApp.services.inspectionSummaryService = {};
lupapisteApp.services.operationFilterService = {};
lupapisteApp.services.organizationFilterService = {};
lupapisteApp.services.organizationTagsService = {};
lupapisteApp.services.publishBulletinService = {};
lupapisteApp.services.ramService = {};
lupapisteApp.services.scrollService = {};
lupapisteApp.services.sidePanelService = {};
lupapisteApp.services.sutiService = {};
lupapisteApp.services.tagFilterService = {};
lupapisteApp.services.verdictAppealService = {};

lupapisteApp.models = {};
lupapisteApp.models.application = {};
lupapisteApp.models.application.permitType = function() {};
lupapisteApp.models.application.permitSubtype = function() {};
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
lupapisteApp.models.applicationAuthModel.refreshWithCallback = function() {};
lupapisteApp.models.globalAuthModel = {};
lupapisteApp.models.globalAuthModel.ok = function() {};

var repository = {};
repository.load = function() {};

var window = function() {};
window.open = function() {};

var gis = {};
gis.makeMap = function() {};
gis.makeMap.updateSize = function() {};
gis.makeMap.updateSize.center = function() {};

var ko = {};
ko.unwrap = function() {};
