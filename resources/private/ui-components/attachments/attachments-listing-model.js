LUPAPISTE.AttachmentsListingModel = function() {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  self.APPROVED = "ok";
  self.REJECTED = "requires_user_action";

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
  self.appModel = lupapisteApp.models.application;
  self.authModel = lupapisteApp.models.applicationAuthModel;

  self.disposedComputed(function() {
    var id = self.service.applicationId(); // create dependency
    if (id) {
      self.service.queryAll();
    }
  });
  var dispose = self.dispose;
  self.dispose = function() {
    self.service.clearData();
    dispose();
  };

  //
  // Attachment hierarchy
  //

  function findMatchingTag(tags, attachment) {
    return _.find(tags, function(tag) {
      return _.includes(attachment.tags, tag);
    });
  }

  function resolveTagGrouping(attachments, tagGroups) {
    if (!tagGroups || !tagGroups.length) {
      return _.map(attachments, "id");
    }
    return _(attachments)
      .groupBy(_.partial(findMatchingTag, _.map(tagGroups, _.head)))
      .mapValues(function (attachmentsInGroup, tagGroupName) {
        var tagGroup = _.find(tagGroups, function(tagGroup) {
          return _.head(tagGroup) === tagGroupName;
        });
        return resolveTagGrouping(attachmentsInGroup, _.tail(tagGroup));
      })
      .value();
  }

  //
  // Attachments hierarchy
  //

  // Return attachment ids grouped first by type-groups and then by type ids.

  self.getAttachmentsHierarchy = function() {
    var attachments = _.map(self.service.filteredAttachments(), ko.utils.unwrapObservable);
    return groupAttachmentsByTags(attachments);
  };

  function groupAttachmentsByTags(attachments) {
    return resolveTagGrouping(attachments, self.service.tagGroups());
  }

  self.attachmentsHierarchy = ko.pureComputed(self.getAttachmentsHierarchy);


  function getAttachmentInfos(attachmentIds) {
    return _(attachmentIds)
          .map(self.service.getAttachment)
          .filter()
          .value();
  }

  function modelForSubAccordion(subGroup) {
    var attachmentInfos = getAttachmentInfos(subGroup.attachmentIds);
    return {
      type: "sub",
      attachmentInfos: attachmentInfos,
      // all approved or some rejected
      status: ko.pureComputed(self.service.attachmentsStatus(subGroup.attachmentIds)),
      hasContent: ko.pureComputed(function() {
        return attachmentInfos &&
          attachmentInfos.length > 0;
      }),
      hasFile: ko.pureComputed(function() {
        return _.some(attachmentInfos, function(attachment) {
          return !!util.getIn(attachment, ["latestVersion", "fileId"]);
        });
      })
    };
  }

  // If all of the subgroups in the main group are approved -> approved
  // If some of the subgroups in the main group are rejected -> rejected
  // Else null
  function subGroupsStatus(subGroups) {
    return ko.pureComputed(function() {
      if (_.every(_.values(subGroups),
                  function(sg) {
                    return sg.status() === self.APPROVED;
                 }))
      {
        return self.APPROVED;
      } else {
        return _.some(_.values(subGroups),
                      function(sg) {
                        return sg.status() === self.REJECTED;
                     }) ? self.REJECTED : null;
      }
    });
  }

  function someSubGroupsField(subGroups, fieldName) {
    return ko.pureComputed(function() {
      return _.some(_.values(subGroups),
                    function(sg) { return util.getIn(sg, [fieldName]); });
    });
  }

  function modelForMainAccordion(mainGroup) {
    var subGroups = _.mapValues(mainGroup.subGroups, groupToModel);
    return _.merge({
      type: "main",
      status: subGroupsStatus(subGroups),
      hasContent: someSubGroupsField(subGroups, "hasContent"),
      hasFile: someSubGroupsField(subGroups, "hasFile")
    }, subGroups);
  }

  function hierarchyToGroups(hierarchy) {
    return _.mapValues(hierarchy, function(group, name) {
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
    });
  }

  function groupToModel(group) {
    if (group.type === "main") {
      return modelForMainAccordion(group);
    } else {
      return modelForSubAccordion(group);
    }
  }

  self.attachmentGroups = ko.pureComputed(function() {
    return _.mapValues(hierarchyToGroups(self.attachmentsHierarchy()),
                       groupToModel);
  });

   function getAttachmentsForGroup(groupPath) {
    function getAttachments(group) {
      if (_.isPlainObject(group)) {
        return _.flatten(_.map(_.values(group), getAttachments));
      } else {
        return group;
      }
    }
    var group = util.getIn(self.attachmentsHierarchy(), groupPath);
    return getAttachments(group);
  }

   function getDataForGroup(groupPath) {
    return ko.pureComputed(function() {
      return util.getIn(self.attachmentGroups(), groupPath);
    });
  }

  function getOperationLocalization(operationId) {
    var operation = _.find(lupapisteApp.models.application._js.allOperations, ["id", operationId]);
    return _.filter([loc([operation.name, "_group_label"]), operation.description]).join(" - ");
  }

  function groupToAccordionName(groupPath) {
    var opIdRegExp = /^op-id-([1234567890abcdef]{24})$/i,
        key = _.last(groupPath);
    if (opIdRegExp.test(key)) {
      return getOperationLocalization(opIdRegExp.exec(key)[1]);
    } else {
      return loc(["application", "attachments", key]);
    }
  }

  function getDataForAccordion(groupPath) {
    return {
      name: groupToAccordionName(groupPath),
      path: groupPath,
      open: ko.observable(),
      data: ko.pureComputed(function() {
        return modelForSubAccordion({
          name: groupToAccordionName(groupPath),
          attachmentIds: getAttachmentsForGroup(groupPath)
        });
      })
    };
  }

  function attachmentTypeLayout(groupPath, tagGroups) {
    if (tagGroups.length) {
      return {
        name: groupToAccordionName(groupPath),
        path: groupPath,
        open: ko.observable(),
        data: getDataForGroup(groupPath),
        accordions: _.map(tagGroups, function(tagGroup) {
          return attachmentTypeLayout(groupPath.concat(_.head(tagGroup)), _.tail(tagGroup));
        })
      };
    } else {
      return getDataForAccordion(groupPath);
    }
  }

  function toggleOpen(groups, bool) {
    groups.open(bool);
    if (groups.accordions) {
      _.map(groups.accordions,
        function(group) {toggleOpen(group, bool);});
    }
  }

  self.openAll = function() {
    if (self.groups && self.groups().open) {
      toggleOpen(self.groups(), true);
    }
  };

  self.toggleAll = function() {
    if (self.groups && self.groups().open) {
      toggleOpen(self.groups(), !(self.groups().open()));
    }
  };

  // auto-open all accordions when new filtered results are available
  self.autoOpener = ko.computed(function() {
    if(self.service.filteredAttachments()) {
      self.openAll();
    }
  });

  // entry point for templates to access model data
  self.groups = ko.computed(function() {
    if (self.service.tagGroups().length) {
      return attachmentTypeLayout([], self.service.tagGroups());
    } else {
      return {};
    }
  });



  self.newAttachment = function() {
    attachment.initFileUpload({
      applicationId: self.appModel.id(),
      attachmentId: null,
      attachmentType: null,
      typeSelector: true,
      opSelector: lupapisteApp.models.application.primaryOperation()["attachment-op-selector"](),
      archiveEnabled: self.authModel.ok("permanent-archive-enabled")
    });
    LUPAPISTE.ModalDialog.open("#upload-dialog");
  };

  self.onUploadDone = function() {
    self.service.queryAll();
  };
  hub.subscribe("upload-done", self.onUploadDone);

  self.onTemplateCreateDone = function() {
    self.service.queryAll();
  };

  function AttachmentTemplatesModel() {
    var templateModel = this;

    templateModel.ok = function(ids) {
      ajax.command("create-attachments", {id: self.appModel.id(), attachmentTypes: ids})
        .success(self.onTemplateCreateDone)
        .complete(LUPAPISTE.ModalDialog.close)
        .call();
    };

    templateModel.init = function() {
      templateModel.initDone = true;
      templateModel.selectm = $("#dialog-add-attachment-templates-v2 .attachment-templates").selectm();
      templateModel.selectm
        .allowDuplicates(true)
        .ok(templateModel.ok)
        .cancel(LUPAPISTE.ModalDialog.close);
      return templateModel;
    };

    templateModel.show = function() {
      if (!templateModel.initDone) {
        templateModel.init();
      }

      var data = _.map(self.appModel.allowedAttachmentTypes(), function(g) {
        var groupId = g[0];
        var groupText = loc(["attachmentType", groupId, "_group_label"]);
        var attachmentIds = g[1];
        var attachments = _.map(attachmentIds, function(a) {
          var id = {"type-group": groupId, "type-id": a};
          var text = loc(["attachmentType", groupId, a]);
          return {id: id, text: text};
        });
        return [groupText, attachments];
      });
      templateModel.selectm.reset(data);
      LUPAPISTE.ModalDialog.open("#dialog-add-attachment-templates-v2");
      return templateModel;
    };
  }
  self.attachmentTemplatesModel = new AttachmentTemplatesModel();
  self.attachmentTemplatesAdd = function() {
    self.attachmentTemplatesModel.show();
  };

  self.startStamping = function() {
    hub.send("start-stamping", {application: self.appModel});
  };

  self.canStamp = function() {
    return self.authModel.ok("stamp-attachments");
  };

  self.signAttachments = function() {
    hub.send("sign-attachments", {application: self.appModel});
  };

  self.canSign = function() {
    return self.authModel.ok("sign-attachments");
  };

  self.hasFile = ko.pureComputed(function() {
    return _(self.attachmentGroups()).invokeMap("hasFile").some();
  });

};
