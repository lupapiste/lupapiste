var stamping = (function() {
  "use strict";

  var model = {
    stampingMode: ko.observable(false),
    authorization: lupapisteApp.models.applicationAuthModel,
    appModel: lupapisteApp.models.application,
    attachments: lupapisteApp.services.attachmentsService.attachments,
    pending: ko.observable(false),
    stamps: ko.observableArray([]),
    selectedStampId: ko.observable(null),

    cancelStamping: function() {
      model.stampingMode(false);
      var id = pageutil.subPage();
      pageutil.openPage("application/" + id, "attachments");
    },

    reloadStamps: function(param) {
      param.stamps([]);
      param.stampsChanged(false);
      loadStampTemplates(param.application.id());
    }
  };

  function findRowData (rows, type) {
    var foundValue = null;
    _.each( rows, function( row ) {
      foundValue = _.get(_.find( row, {type: type} ), "value");
      return !foundValue;
    });
    return foundValue;
  }

  function updateRowData (type, value, rows) {
    return _.map(rows,  function (row) {
      return _.map(row , function(object) {
        if (object.type === type) {
          return {type: type, value: value};
        }
        return object;
      });
    });
  }

  function loadStampTemplates(appId) {
    ajax.query("custom-stamps", {
      id: appId})
      .success(function (data) {
        _.each(data.stamps, function (stamp) {
          var existingStamp = _.find(model.stamps(), function(modelStamp) {
            return modelStamp.id === stamp.id;
          });
          if (existingStamp) {
            stamp.rows = (updateRowData("extra-text", findRowData(existingStamp.rows, "extra-text"), stamp.rows));
          }
        });
        model.stamps(data.stamps);
        if (!ko.unwrap(model.selectedStampId)) { // if nothing is selected, use first stamp by default
          model.selectedStampId(model.stamps()[0].id);
        }
      }).call();
  }

  hub.onPageLoad("stamping", function() {
    pageutil.showAjaxWait();
    if ( pageutil.subPage() ) {
      if ( model.appModel.id() !== pageutil.subPage() ) {
        // refresh as ID is undefined or LP-id in URL changed
        var appId = pageutil.subPage();
        model.pending(true);
        model.stampingMode(false);
        loadStampTemplates(appId);
        repository.load(appId, _.noop, function(application) {
          lupapisteApp.setTitle(application.title);
          model.stampingMode(true);
        }, true);
      } else { // appModel already initialized, show stamping
        model.pending(true);
        lupapisteApp.services.attachmentsService.queryAll(); // subscription below will set pending to false
        loadStampTemplates(model.appModel.id());
        model.stampingMode(true);
        lupapisteApp.setTitle(model.appModel.title());
      }
    } else {
      error("No application ID provided for stamping");
      LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
    }
    pageutil.hideAjaxWait();
  });

  hub.onPageUnload("stamping", function() {
    model.stampingMode(false);
    // refresh attachments for application page
    lupapisteApp.services.attachmentsService.queryAll();
  });

  hub.subscribe({eventType: "attachmentsService::query", query: "attachments"}, function() {
    model.pending(false);
  });

  ko.components.register("stamping-component", {
    viewModel: LUPAPISTE.StampModel,
    template: {element: "stamp-attachments-template"}
  });

  $(function() {
    $("#stamping-container").applyBindings(model);
  });
})();
