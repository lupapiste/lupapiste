LUPAPISTE.AttachmentsListingAccordionModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.attachmentsService;

  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.pageName = params.pageName;
  self.level = params.level;

  var groupPath = params.path;

  var filterSet = service.getFilters( self.pageName );
  var tagGroup  = service.getTagGroups( self.pageName ).getTagGroup( groupPath );

  self.open = tagGroup.accordionOpen;
  self.groups = tagGroup.subGroups;

  var attachments = self.disposedPureComputed(function() {
    return _.filter(service.attachments(), isInGroup);
  });

  self.filteredAttachments = self.disposedPureComputed(function() {
    return filterSet.apply(attachments());
  });

  var fileCount = self.disposedPureComputed(function() {
    return _.filter(self.filteredAttachments(), function(att) {
      return util.getIn(att, ["latestVersion", "fileId"]);
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

  self.downloadAll = function() {
    service.downloadAttachments(_.map(self.filteredAttachments(),
                                      _.ary( _.partialRight( util.getIn, ["id"]), 1 )));
  };

  self.downloadAllText = self.disposedPureComputed(function() {
    return loc("download") + " " + fileCount() + " " + loc(fileCount() === 1 ? "file" : "file-plural-partitive");
  });

  function isInGroup(attachment) {
    return _.every(groupPath, function(groupName) {
      return _.includes(ko.unwrap(attachment).tags, groupName);
    });
  }

  function getOperationLocalization(operationId) {
    var allOperations = lupapisteApp.models.application.allOperations();
    var operation = ko.toJS(_.find(allOperations, function(op) {
      return util.getIn(op, ["id"]) === operationId;
    }));
    return operation
      ? _.filter([loc([operation.name, "_group_label"]), operation.description]).join(" - ")
      : "";
  }

  self.accordionName = self.disposedPureComputed(function() {
    var opIdRegExp = /^op-id-([1234567890abcdef]{24})$/i;
    var key = _.last(groupPath);
    if (opIdRegExp.test(key)) {
      return getOperationLocalization(opIdRegExp.exec(key)[1]);
    } else {
      return loc(["application", "attachments", key]);
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
