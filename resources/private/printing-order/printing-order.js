;(function() {
  "use strict";

  hub.onPageLoad("printing-order", function() {
    pageutil.showAjaxWait();
    if ( pageutil.subPage() ) {
      if ( lupapisteApp.models.application.id() !== pageutil.subPage() ) {
        // refresh as ID is undefined or LP-id in URL changed
        var appId = pageutil.subPage();
        repository.load(appId, _.noop, function(application) {
          lupapisteApp.setTitle(application.title);
        }, true);
      }
    }
    pageutil.hideAjaxWait();
  });


  $(function() {
    $("#printing-order").applyBindings({
      applicationModel: lupapisteApp.models.application,
      backToApplication: function() {
        var id = pageutil.subPage();
        pageutil.openPage("application/" + id, "attachments");
      }
    });
  });

})();
