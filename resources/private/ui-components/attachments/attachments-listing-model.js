LUPAPISTE.AttachmentsListingModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var legendTemplate = _.template( "<div class='like-btn'>"
                                   + "<i class='<%- icon %>'></i>"
                                   + "<span><%- text %></span>"
                                   + "</div>");
  var legend = [["lupicon-circle-check positive", "ok.title"],
                ["lupicon-circle-star primary", "new.title"],
                ["lupicon-circle-attention negative", "missing.title"],
                ["lupicon-circle-pen positive", "attachment.signed"],
                ["lupicon-circle-arrow-up positive", "application.attachmentSentDate"],
                ["lupicon-circle-stamp positive", "attachment.stamped"],
                ["lupicon-circle-section-sign positive", "attachment.verdict-attachment"],
                ["lupicon-lock primary", "attachment.not-public-attachment"]];

  self.legendHtml = _( legend )
    .map( function( leg ) {
      return legendTemplate( {icon: _.first( leg ),
                              text: loc( _.last( leg ))});
    })
    .value()
    .join("<br>");

  self.service = lupapisteApp.services.attachmentsService;
  self.disposedComputed(function() {
    console.log("AttachmentListingModel applicationId computed called");
    var id = self.service.applicationId(); // create dependency
    if (id) {
      self.service.queryAll();
    } else {
      console.log("skipping queryAll, application id=", id);
    }
  });

  var dispose = self.dispose;
  self.dispose = function() {
    self.service.changeScheduledNotNeeded();
    self.service.clearData();
    dispose();
  };

  function getOperationLocalization(operationId) {
    var operation = _.find(lupapisteApp.models.application._js.allOperations, ["id", operationId]);
    return _.filter([loc([operation.name, "_group_label"]), operation.description]).join(" - ");
  }

  function getLocalizationForDefaultGroup(groupPath) {
    if (groupPath.length === 1) {
      return loc("application.attachments.general");
    } else {
      return loc("application.attachments.other");
    }
  }

  function groupToAccordionName(groupPath) {
    var opIdRegExp = /^op-id-([1234567890abcdef]{24})$/i,
        key = _.last(groupPath);
    if (opIdRegExp.test(key)) {
      return getOperationLocalization(opIdRegExp.exec(key)[1]);
    } else if (_.last(groupPath) === "default") {
      return getLocalizationForDefaultGroup(groupPath);
    } else {
      return loc(["application", "attachments", key]);
    }
  }

  function getDataForAccordion(groupPath) {
    return {
      lname: groupToAccordionName(groupPath),
      open: ko.observable(),
      data: ko.pureComputed(function() {
        return self.service.modelForSubAccordion({
          lname: groupToAccordionName(groupPath),
          attachmentIds: self.service.getAttachmentsForGroup(groupPath)
        });
      })
    };
  }

  function attachmentTypeLayout(groupPath, tagGroups) {
    console.log("attachmentTypeLayout: groupPath", groupPath);
    if (tagGroups.length) {
      return {
        lname: groupToAccordionName(groupPath),
        open: ko.observable(),
        data: self.service.getDataForGroup(groupPath),
        accordions: _.map(tagGroups, function(tagGroup) {
          return attachmentTypeLayout(groupPath.concat(_.head(tagGroup)), _.tail(tagGroup));
        })
      };
    } else {
      return getDataForAccordion(groupPath);
    }
  }

  // entry point for templates to access model data
  self.groups = ko.computed(function() {
    if (self.service.tagGroups().length) {
      return attachmentTypeLayout([], self.service.tagGroups());
    } else {
      return {};
    }
  });


};
