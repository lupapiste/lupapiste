(function() {
  "use strict";

  var model = {
    selectingMode: ko.observable(false),
    authorization: undefined,
    appModel: undefined,
    filteredAttachments: undefined,

    doMoveAttachmetsToBackingSystem: function(selectedAttachmentsIds) {
      return function() {
        var id = model.appModel.id();
        ajax.command("move-attachments-to-backing-system", {
          id: id,
          lang: loc.getCurrentLanguage(),
          attachmentIds: selectedAttachmentsIds
        })
        .success(function() {
          window.location.hash = "!/application/" + id + "/attachments";
          repository.load(id);
        })
        .error(function() {
          // Correct error message
          notify.error(loc("error.dialog.title"), loc("attachment.set-attachments-as-verdict-attachment.error"));
          repository.load(id);
        })
        .call();
      };
    },

    moveAttachmetsToBackingSystem: function(selectedAttachmentsIds) {
      LUPAPISTE.ModalDialog.showDynamicYesNo(
        loc("application.attachmentsMoveToBackingSystem"),
        loc("application.attachmentsMoveToBackingSystem.confirmationMessage"),
        {title: loc("yes"), fn: model.doMoveAttachmetsToBackingSystem(selectedAttachmentsIds)},
        {title: loc("no")}
      );
    },

    cancelSelecting: function() {
      var id = model.appModel.id();
      model.selectingMode(false);
      model.appModel = undefined;
      model.filteredAttachments = undefined;
      model.authorization = undefined;

      // TODO hardcoded back link
      window.location.hash="!/application/" + id + "/attachments";
      repository.load(id);
    }
  };

  function filterAttachments(attachments) {
    return _(attachments)
      .filter(function(a) {
        return (a.versions.length > 0 && (!a.sent || _.last(a.versions).created > a.sent) && !(_.includes(["verdict", "statement"], util.getIn(a, ["target", "type"]))));
      })
      .each(function(a) {
        a.selected = true;
      })
      .value();
  }

  function init(appModel) {
    model.appModel = appModel;
    model.filteredAttachments = filterAttachments(ko.mapping.toJS(appModel.attachments()));
    model.authorization = authorization.create();
    model.authorization.refresh(model.appModel.id());

    window.location.hash="!/move-attachments-to-backing-system-select/" + model.appModel.id();
  }

  hub.onPageLoad("move-attachments-to-backing-system-select", function() {
    if ( pageutil.subPage() ) {
      if ( !model.appModel || model.appModel.id() !== pageutil.subPage() ) {
        // refresh
        model.selectingMode(false);

        var appId = pageutil.subPage();
        repository.load(appId, null, function(application) {
          lupapisteApp.setTitle(application.title);

          model.authorization = authorization.create();
          model.appModel = new LUPAPISTE.ApplicationModel();
          model.authorization.refresh(application);

          ko.mapping.fromJS(application, {}, model.appModel);

          model.filteredAttachments = filterAttachments(application.attachments);

          model.selectingMode(true);
        });
      } else { // appModel already initialized, show the multiselect view
        model.selectingMode(true);
        lupapisteApp.setTitle(model.appModel.title());
      }
    } else {
      error("No application ID provided for verdict attachments multiselect");
      LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
    }
  });

  hub.onPageUnload("move-attachments-to-backing-system-select", function() {
    model.selectingMode(false);
  });

  hub.subscribe("start-moving-attachments-to-backing-system", function() {
    init(lupapisteApp.models.application);
  });

  $(function() {
    $("#attachment-multiselect-container").applyBindings(model);
  });
})();
