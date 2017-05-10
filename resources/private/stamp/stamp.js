var stamping = (function() {
  "use strict";

  var model = {
    stampingMode: ko.observable(false),
    authorization: null,
    appModel: null,
    attachments: null,
    pending: ko.observable(false),
    stampFields: {
      text: ko.observable(),
      date: ko.observable(),
      organization: null,
      xMargin: ko.observable(),
      yMargin: ko.observable(),
      page: ko.observable(),
      transparency: ko.observable(),
      extraInfo: ko.observable(),
      kuntalupatunnus: ko.observable(),
      section: ko.observable()
    },
    stamps: ko.observableArray([]),

    cancelStamping: function() {
      var id = pageutil.subPage();
      model.stampingMode(false);
      model.appModel = null;
      model.attachments = null;
      model.authorization = null;
      pageutil.openPage("application/" + id, "attachments");
    },

    resetStamping: function() {
      model.stampingMode(false);
      model.appModel = null;
      model.attachments = null;
      model.authorization = null;

      hub.send("page-load", {pageId: "stamping"});
    },

    reloadStamps: function(param) {
      param.stamps([]);
      param.stampsChanged(false);
      loadStampTemplates(param.application.id());
    }
  };

  function resetStampFields() {
    var fields = model.stampFields;
    fields.text(loc("stamp.verdict"));
    fields.date(new Date());
    fields.organization = null;
    fields.xMargin("10");
    fields.yMargin("200");
    fields.page("first");
    fields.transparency("");
    fields.extraInfo("");
    fields.kuntalupatunnus("");
    fields.section("");
  }

  function loadStampTemplates(appId) {
    ajax.query("custom-stamps", {
      id: appId})
      .success(function (data) {
        _.each(data.stamps, function (stamp) {
          var existingStamp = _.find(model.stamps(), function(modelStamp) {
            return modelStamp.id === stamp.id;
          });
          if (!existingStamp) {
            model.stamps.push(stamp);
          }
        });
      }).call();
  }

  function initStamp(appModel) {
    model.appModel = appModel;
    model.attachments = lupapisteApp.services.attachmentsService.attachments;
    model.authorization = lupapisteApp.models.applicationAuthModel;
    pageutil.openPage("stamping", model.appModel.id());
  }

  hub.onPageLoad("stamping", function() {
    if ( pageutil.subPage() ) {
      if ( !model.appModel || model.appModel.id() !== pageutil.subPage() ) {
        // refresh
        var appId = pageutil.subPage();
        model.pending(true);
        model.stampingMode(false);
        loadStampTemplates(appId);
        repository.load(appId, _.noop, function(application) {
          lupapisteApp.setTitle(application.title);
          model.authorization = lupapisteApp.models.applicationAuthModel;
          model.appModel = lupapisteApp.models.application;
          ko.mapping.fromJS(application, {}, model.appModel);
          model.appModel._js = application;
          model.attachments = lupapisteApp.services.attachmentsService.attachments;
          model.stampingMode(true);
        }, true);
      } else { // appModel already initialized, show stamping
        model.stampingMode(true);
        lupapisteApp.setTitle(model.appModel.title());
      }
    } else {
      error("No application ID provided for stamping");
      LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
    }
    pageutil.hideAjaxWait();
  });

  // This component is never disposed. We do reset initially and
  // afterwards always on leaving an application. This way the
  // initialization works both for regularly opening stamping view and
  // for the view reload.
  resetStampFields();

  hub.subscribe( "contextService::leave", resetStampFields );

  hub.onPageUnload("stamping", function() {
    model.stampingMode(false);
    model.appModel = null;
    model.attachments = null;
    model.pending(true);
    lupapisteApp.services.attachmentsService.queryAll();
    model.authorization = null;
  });

  hub.subscribe({eventType: "attachmentsService::query", query: "attachments"}, function() {
    model.pending(false);
  });

  hub.subscribe("start-stamping", function(param) {
    loadStampTemplates(param.application.id());
    pageutil.showAjaxWait();
    if (model.stamps().length > 0) {
      initStamp(param.application);
    } else {
      _.delay(initStamp, 2000, param.application);
    }
  });

  ko.components.register("stamping-component", {
    viewModel: LUPAPISTE.StampModel,
    template: {element: "stamp-attachments-template"}
  });

  $(function() {
    $("#stamping-container").applyBindings(model);
  });
})();
