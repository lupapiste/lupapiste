LUPAPISTE.AttachmentMultiSelect = function() {
  "use strict";

  var self = this;

  var typesNotTransmittedToBackingSystem = // HACK: Duplicated from lupapiste-commons.attachment-types
    {muut: ["paatos", "paatosote", "sopimus"],
     paatoksenteko: ["paatoksen_liite"]};

  function isTransmittable(attachment) {
    var type = attachment.type;
    return !_.includes(typesNotTransmittedToBackingSystem[type["type-group"]], type["type-id"]);
  }

  // Overwrite this if needed
  self.filterAttachments = function(attachments) {
    return _(attachments)
      .filter(function(a) {
        var versions = util.getIn(a, ["versions"]);
        var latestTs = util.getIn(_.last(versions), ["created"]);
        var sent = util.getIn(a, ["sent"]);
        var fetched = util.getIn(a, ["fetched"]);
        return (isTransmittable(ko.mapping.toJS(a))
                && !_.isEmpty(versions)
                && (!sent || latestTs > sent)
                && (!fetched || latestTs > fetched)
                && !(_.includes(["verdict", "statement"], util.getIn(a, ["target", "type"]))));
      })
      .map(function(a) {
        return _.defaults( {selected:true}, ko.mapping.toJS(a));
      })
      .value();
  };

  self.model = {
    selectingMode: ko.observable(false),
    authorization: undefined,
    appModel: undefined,
    filteredAttachments: ko.computed(function() {
      var attachments = lupapisteApp.services.attachmentsService.attachments();
      return self.filterAttachments(attachments);
    }),

    moveAttachmets: undefined,

    cancelSelecting: function() {
      self.model.selectingMode(false);
      self.model.authorization = undefined;
      self.model.appModel.open("attachments");
      self.model.appModel.reload();
      self.model.appModel = undefined;
    }
  };

  self.hash = undefined;

  self.init = function(appModel) {
    self.model.appModel = appModel;
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

          self.model.selectingMode(true);
        });
      } else { // appModel already initialized, show the multiselect view
        self.model.selectingMode(true);
        lupapisteApp.setTitle(self.model.appModel.title());
      }
    } else {
      error("No application ID provided for attachments multiselect");
      repository.applicationNotFoundDialog();
    }
  };

  self.onPageUnload = function() {
    self.model.selectingMode(false);
  };

  self.subscribe = function() {
    self.init(lupapisteApp.models.application);
  };
};
