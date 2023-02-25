LUPAPISTE.AttachmentsListingAccordionModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;
  var accordionService = lupapisteApp.services.accordionService;

  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.pageName = params.pageName;
  self.level = params.level;
  self.upload = params.upload;
  self.options = params.options;
  self.attachments = params.attachments;

  self.accordionIndicators = params.options.accordionIndicators;

  var groupPath = params.path;

  var filterSet = service.getFilters( self.pageName );
  var tagGroup  = service.getTagGroups( self.pageName ).getTagGroup( groupPath );

  self.open = tagGroup.accordionOpen;
  self.groups = tagGroup.subGroups;

  var attachments = self.disposedPureComputed(function() {
    return _.filter(self.attachments(), isInGroup);
  });

  self.filteredAttachments = self.disposedPureComputed(function() {
    return filterSet.apply(attachments());
  });

  self.fileIds = self.disposedPureComputed(function() {
    return _(self.filteredAttachments()).map(function(a) { return a().id; }).value();
  });

  self.areAllSelected = self.disposedPureComputed(function() {
    if (_.hasIn(self.options, "pageModel.selectedFiles")) {
      var selectedFiles = _.map(self.options.pageModel.selectedFiles(), function (f) { return f.id; });
      return _.every(self.fileIds(), function (id) { return _.includes(selectedFiles, id); });
    }
  });

  var fileCount = self.disposedPureComputed(function() {
    return _.filter(self.filteredAttachments(), function(att) {
      return util.getIn(att, ["latestVersion", "filename"]);
    }).length;
  });

  self.name = _.last(groupPath);

  // If some of the attachments are approved and the rest not needed -> approved
  // If some of the attachments are rejected -> rejected
  // If some of the needed attachments are missing files -> rejected
  // Else null
  self.status = self.disposedPureComputed(function() {
    var atts = attachments();
    if ( _.some( atts, service.isApproved ) &&
         _.every( atts, _.overSome([service.isApproved, service.isNotNeeded]) ) ) {
      return service.APPROVED;
    } else if (_.some( atts, service.isRejected )
               || _.some( atts, service.isMissingFile )) {
      return  service.REJECTED;
    }
    return null;
  });

  self.hasContent = self.disposedPureComputed(function() {
    return !_.isEmpty(self.filteredAttachments());
  });

  self.hasFile = self.disposedPureComputed(function() {
    return fileCount() > 0;
  });

  self.getFileCount = self.disposedPureComputed( function() {
    return fileCount();
  });

  self.isDownloadAllVisible = function(selfFiles, parentFiles) {
    // Don't show the inner button if the outer button is the same
    return self.options.downloadableRows && selfFiles > 0 && selfFiles !== parentFiles;
  };

  self.downloadAll = function() {
    service.downloadAttachments(_.map(self.filteredAttachments(),
                                      _.ary( _.partialRight( util.getIn, ["id"]), 1 )));
  };

  self.downloadAllText = self.disposedPureComputed(function() {
    return loc("download") + " " + fileCount() + " " + loc(fileCount() === 1 ? "file" : "file-plural-partitive");
  });

  self.isSelectAllVisible = function(selfFiles, parentFiles) {
    return self.options.selectableRows && selfFiles > 0 && selfFiles !== parentFiles;
  };

  self.toggleSelectAll = function() {
    self.options.pageModel.selectFilesById(self.fileIds());
  };

  function isInGroup(attachment) {
    return _.every(groupPath, function(groupName) {
      return _.includes(ko.unwrap(attachment).tags, groupName);
    });
  }

  self.accordionName = self.disposedPureComputed(function() {
    if (accordionService) {
      return accordionService.attachmentAccordionName(_.last(groupPath));
    } else {
      return "";
    }
  });

  self.toggleAll = function() {
    tagGroup.toggleAll();
  };

  // auto-open accordion when new filtered results are available
  var attachmentCount = self.disposedComputed(function() { return self.filteredAttachments().length; });
  self.subscribeChanged(attachmentCount, function(count, previousCount) {
    if (count > previousCount) {
      tagGroup.toggle(true);
    }
  });

};
