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
  self.attachmentsHierarchy = ko.pureComputed(self.service.getAttachmentsHierarchy);
  self.rollupToggle = ko.observable();
  self.innerToggle = ko.observable();
  self.linkToggle = ko.observable();
  self.rollupStatus = ko.observable( "ok");

  function attachmentFn(f) {
    return function(attachmentId) {
      return function() {
        f(attachmentId);
      };
    };
  }

  function idToAttachment(attachmentId) {
    var attachment = self.service.getAttachment(attachmentId);
    if (attachment) {
      return attachment;
    } else {
      return null;
    }
  }

  self.modelForAttachmentInfo = function(attachmentIds) {
    return {
      approveAttachment: attachmentFn(self.service.approveAttachment),
      rejectAttachment:  attachmentFn(self.service.rejectAttachment),
      removeAttachment:  attachmentFn(self.service.removeAttachment),
      isApproved:        self.service.isApproved,
      isRejected:        self.service.isRejected,
      isNotNeeded:       self.service.isNotNeeded,
      attachments:       _.map(attachmentIds, idToAttachment)
    };
  };

  function modelForSubAccordion(subGroup) {
    return {
      type: "sub",
      ltitle: subGroup.name, // TODO
      attachmentInfos: self.modelForAttachmentInfo(subGroup.attachmentIds),
      status: ko.pureComputed(self.service.attachmentsStatus(subGroup.attachmentIds)),
      hasAttachments: ko.pureComputed(function() {
        return subGroup.attachmentIds &&
          subGroup.attachmentIds.length > 0;
      })
    };
  }

  function subGroupsStatus(subGroups) {

    return ko.pureComputed(function() {
      if (_.every(_.values(subGroups),
                  function(sg) {
                    return sg.status() === self.service.APPROVED;
                 }))
      {
        return self.service.APPROVED;
      } else {
        return _.some(_.values(subGroups),
                      function(sg) {
                        return sg.status() === self.service.REJECTED;
                     })
        ? self.service.REJECTED : null;
      }
    });
  }

  function someSubGroupsHaveAttachments(subGroups) {
    return ko.pureComputed(function() {
      return _.some(_.values(subGroups),
                    function(sg) { return sg.hasAttachments(); });
    });
  }

  function modelForMainAccordion(mainGroup) {
    var subGroups = _.mapValues(mainGroup.subGroups, groupToModel);
    return _.merge({
      type: "main",
      ltitle: mainGroup.name, // TODO
      status: subGroupsStatus(subGroups),
      hasAttachments: someSubGroupsHaveAttachments(subGroups)
    }, subGroups);
  }

  function hierarchyToGroups(hierarchy) {
    return _(hierarchy).mapValues(function(group, name) {
      if (_.isPlainObject(group)) {
        return {
          type: "main",
          name: name,
          subGroups: hierarchyToGroups(group)
        };
      } else {
        return {
          type: "sub",
          name: name,
          attachmentIds: group
        };
      }
    }).value();
  }

  function groupToModel(group) {
    if (group.type === "main") {
      return modelForMainAccordion(group);
    } else {
      return modelForSubAccordion(group);
    }
  }

  self.verdicts = ko.pureComputed(function() {
    return  _.mapValues(hierarchyToGroups(self.attachmentsHierarchy()),
                               groupToModel);
  });

  function getDataForGroup() {
    var args = _.toArray(arguments);
    return ko.pureComputed(function() {
      return  _.merge(_.get(self.verdicts(), args));
    });
  }

  function getDataForAccordion() {
    var args = _.toArray(arguments);
    return {
      text: _.last(args),
      open: ko.observable(),
      data: _.spread(getDataForGroup)(args)
    };
  }

  self.groups = {
    preVerdict: {
      open: ko.observable(),
      data: getDataForGroup("preVerdict"),
      yleiset: getDataForAccordion("preVerdict", "yleiset"),
      erityissuunnitelmat: {
        open: ko.observable(),
        data: getDataForGroup("preVerdict", "erityissuunnitelmat"),
        kvv_suunnitelma: getDataForAccordion("preVerdict",
                                             "erityissuunnitelmat",
                                             "kvv_suunnitelma"),
        rakennesuunnitelma: getDataForAccordion("preVerdict",
                                                "erityissuunnitelmat",
                                                "rakennesuunnitelma")
      }
    },
    postVerdict: {
      open: ko.observable()
    }
  };

  self.getAccordionToggle = function() {
    return _.get(self.groups, _.toArray(arguments)).open;
  };

};
