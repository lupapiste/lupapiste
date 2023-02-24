// Notification service for context changes.
// Sends event via hub when the application model id has changed and
// the current page is part of the application view.
LUPAPISTE.ContextService = function() {
  "use strict";
  var self = this;

  var appModel = lupapisteApp.models.application;
  var latestAppId = ko.observable();

  function isAppView( appId ) {
    // Inforequest only has inforequest and attachment views.
    var infoRequestCheck = appModel.infoRequest()
        ? _.includes( ["inforequest", "attachment"],
                      pageutil.getPage() )
        : true;

    return appId
      && pageutil.hashApplicationId() === appId
      && infoRequestCheck;
  }

  function checkContext() {
    var appId = appModel.id();
    var inView = isAppView( appId );
    if( latestAppId() !== appId && inView ) {
      latestAppId(appId);
      hub.send("contextService::enter", {applicationId: appId});
    }
    if( latestAppId() && !inView) {
      latestAppId(null);
      hub.send("contextService::leave");
    }
  }

  // We check for the context switch both when hash or application
  // changes. The latter is needed when application view is reloaded.
  // TODO check if we could get rid of jquery hashchange.js dependency
  $(window).on("hashchange", checkContext);
  hub.subscribe( "application-model-updated", checkContext );


  self.applicationId = latestAppId;

};
