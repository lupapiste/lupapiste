LUPAPISTE.AttachmentsAccordionsModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.pageName = params.pageName;

  var filterSet = service.getFilters( self.pageName );
  var tagGroupSet  = service.getTagGroups( self.pageName );

  self.groups = tagGroupSet.getTagGroup();

  var attachments = service.attachments;

  var filteredAttachments = self.disposedPureComputed(function() {
    return filterSet.apply(attachments());
  });

  var fileCount = self.disposedPureComputed(function() {
    return _.filter(filteredAttachments(), function(att) {
      return util.getIn(att, ["latestVersion", "fileId"]);
    }).length;
  });

  self.hasFile = self.disposedPureComputed(function() {
    return fileCount() > 0;
  });

  self.downloadAll = function() {
    service.downloadAttachments(filteredAttachments());
  };

  self.downloadAllText = self.disposedPureComputed(function() {
    return loc("download") + " " + fileCount() + " " + loc(fileCount() === 1 ? "file" : "file-plural-partitive");
  });

  self.toggleAll = function() {
    tagGroupSet.toggleAll();
  };

};
