LUPAPISTE.AttachmentMultiSelect = function() {
  "use strict";

  var self = this;

  self.model = {
    selectingMode: ko.observable(false),
    authorization: undefined,
    appModel: undefined,
    filteredAttachments: undefined,

    moveAttachmets: undefined,

    cancelSelecting: function() {
      self.model.selectingMode(false);
      self.model.filteredAttachments = undefined;
      self.model.authorization = undefined;
      self.model.appModel.open("attachments");
      self.model.appModel.reload();
      self.model.appModel = undefined;
    }
  };

  self.hash = undefined;
  self.filterAttachments = undefined;

  self.init = function(appModel) {
    self.model.appModel = appModel;
    self.model.filteredAttachments = self.filterAttachments(ko.mapping.toJS(appModel.attachments()));
    self.model.authorization = lupapisteApp.models.applicationAuthModel;

    window.location.hash = self.hash + self.model.appModel.id();
  };

  self.onPageLoad = function() {
    if ( pageutil.subPage() ) {
      if ( !self.model.appModel || self.model.appModel.id() !== pageutil.subPage() ) {
        // refresh
        self.model.selectingMode(false);

        var appId = pageutil.subPage();
        repository.load(appId, _.noop, function(application) {
          lupapisteApp.setTitle(application.title);

          self.model.authorization = lupapisteApp.models.applicationAuthModel;
          self.model.appModel = lupapisteApp.models.application;

          ko.mapping.fromJS(application, {}, self.model.appModel);

          self.model.filteredAttachments = self.filterAttachments(application.attachments);

          self.model.selectingMode(true);
        });
      } else { // appModel already initialized, show the multiselect view
        self.model.selectingMode(true);
        lupapisteApp.setTitle(self.model.appModel.title());
      }
    } else {
      error("No application ID provided for attachments multiselect");
      LUPAPISTE.ModalDialog.open("#dialog-application-load-error");
    }
  };

  self.onPageUnload = function() {
    self.model.selectingMode(false);
  };

  self.subscribe = function() {
    self.init(lupapisteApp.models.application);
  };
};
