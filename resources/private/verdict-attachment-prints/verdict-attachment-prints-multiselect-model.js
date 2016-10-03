
(function() {
  "use strict";

  var model = {
    selectingMode: ko.observable(false),
    authorization: undefined,
    appModel: undefined,
    filteredAttachments: ko.pureComputed( function() {
      return _.map(lupapisteApp.services.attachmentsService.attachments(),
                   function( a ) {
                     return _.defaults( {selected: Boolean( a().forPrinting())},
                                        ko.mapping.toJS( a ));
                   });
    }),

    setAttachmentsAsVerdictAttachment: function(selectedAttachmentsIds, unSelectedAttachmentsIds) {
      var id = model.appModel.id();
      ajax.command("set-attachments-as-verdict-attachment", {
        id: id,
        lang: loc.getCurrentLanguage(),
        selectedAttachmentIds: selectedAttachmentsIds,
        unSelectedAttachmentIds: unSelectedAttachmentsIds
      })
      .success(function() {
        model.appModel.open("attachments");
        model.appModel.reload();
      })
      .error(function() {
        notify.error(loc("error.dialog.title"), loc("attachment.set-attachments-as-verdict-attachment.error"));
        repository.load(id);
      })
      .call();
    },

    cancelSelecting: function() {
      model.selectingMode(false);
      model.attachments = null;
      model.authorization = null;

      model.appModel.open("attachments");
      model.appModel = null;
    }
  };

  function initMarking(appModel) {
    model.appModel = appModel;
    model.authorization = lupapisteApp.models.applicationAuthModel;

    pageutil.openPage("verdict-attachments-select", model.appModel.id());
  }

  hub.onPageLoad("verdict-attachments-select", function() {
    if ( pageutil.subPage() ) {
      if ( !model.appModel || model.appModel.id() !== pageutil.subPage() ) {
        // refresh
        model.selectingMode(false);

        var appId = pageutil.subPage();
        repository.load(appId, _.noop, function(application) {
          lupapisteApp.setTitle(application.title);

          model.authorization = lupapisteApp.models.applicationAuthModel;
          model.appModel = lupapisteApp.models.application;

          ko.mapping.fromJS(application, {}, model.appModel);
          model.appModel._js = application;

          model.selectingMode(true);
        }, true);
      } else { // appModel already initialized, show the multiselect view
        model.selectingMode(true);
        lupapisteApp.setTitle(model.appModel.title());
      }
    } else {
      error("No application ID provided for verdict attachments multiselect");
      LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
    }
  });

  hub.onPageUnload("verdict-attachments-select", function() {
    model.selectingMode(false);
  });

  hub.subscribe("start-marking-verdict-attachments", function(param) {
    initMarking(param.application);
  });


  $(function() {
    $("#verdict-attachments-select").applyBindings(model);
  });
})();
