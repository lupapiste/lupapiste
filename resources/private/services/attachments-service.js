//
// Provides services for attachments tab.
//
//

LUPAPISTE.AttachmentsService = function() {
  "use strict";
  var self = this;
  ko.options.deferUpdates = true;
  self.APPROVED = "ok";
  self.REJECTED = "requires_user_action";
  self.SCHEDULED_FOR_NOT_NEEDED = "scheduled_for_not_needed";

  function createArrayModel(array) {
    return ko.observable(array);
  }

  self.attachments = ko.observableArray([]);
  self.tagGroups = ko.observableArray([]);
  self.filters = ko.observableArray([]);

  // observables to be depended on in filters received later from the backend
  // (preconstructed, because we need to know observables to depend on before 
  //  we have information about filters and tags at hand)
  self.freeObservables = [ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false), ko.observable(false)];

  self.activeFilters = ko.pureComputed(function() {
    //_.map(self.internedObservables, function(v, k) { console.log("filter ", k, " = ", v()); })
    return _.filter(self.filtersArray(), function(f) {
      return f.filter() == true});
  });

  self.filteredAttachments = ko.pureComputed(
    function() {
      //console.log("recomputing filteredAttachments");
      return applyFilters(self.attachments(), self.activeFilters());});

  self.queryAll = function queryAll() {
    var fireQuery = function(commandName, responseJsonKey, dataSetter) {
      ajax.query(commandName, {"id": self.applicationId})
        .success(function(data) {
          dataSetter(data[responseJsonKey]);
        })
        .onError("error.unauthorized", notify.ajaxError)
        .call();
    };

    fireQuery("attachments", "attachments", self.setAttachments);
    fireQuery("attachments-tag-groups", "tagGroups", self.setTagGroups);
    fireQuery("attachments-filters", "attachmentsFilters", self.setFilters);
  };

  self.setAttachments = function(data) {
    self.attachments(_.map(data, createArrayModel));
  };

  self.setTagGroups = function(data) {
    self.tagGroups(data);
  };

  self.setFilters = function(data) {
    self.filters(data);
  };

  lupapisteApp.models.application.id.subscribe(function(newId) {
    self.applicationId = newId;
    self.queryAll();
  });

  self.getAttachment = function(attachmentId) {
    return _.find(self.attachments(), function(attachment) {
      return attachment.peek().id === attachmentId;
    });
  };

  self.removeAttachment = function(attachmentId) {
    self.attachments.remove(function(attachment) {
      return attachment().id === attachmentId;
    });
  };

  self.updateAttachment = function(attachmentId, updates) {
    var oldAttachment = self.getAttachment(attachmentId);
    if (oldAttachment) {
      self.getAttachment(attachmentId)(_.merge(oldAttachment(), updates));
    }
  };

  // Approving and rejecting attachments
  self.approveAttachment = function(attachmentId) {
    self.updateAttachment(attachmentId, {state: self.APPROVED});
  };
  self.rejectAttachment = function(attachmentId) {
    self.updateAttachment(attachmentId, {state: self.REJECTED});
  };

  self.setNotNeeded = function(attachmentId, flag ) {
    self.updateAttachment(attachmentId, {notNeeded: !flag ?
                                                    flag :
                                                    self.SCHEDULED_FOR_NOT_NEEDED});
  };

  // When an attachment is set to "not needed" with self.setNotNeeded, it
  // is actually first scheduled for the change. Calling this function
  // will change the state from "scheduled" to "not needed".
  self.changeScheduledNotNeeded = function() {
    var attachmentIds = _(self.attachments.peek()).map(function(attachment) {
      return attachment();
    }).filter({notNeeded: self.SCHEDULED_FOR_NOT_NEEDED}).map("id").value();
    _.forEach(attachmentIds, function(attachmentId) {
      self.updateAttachment(attachmentId, {notNeeded: true});
    });
    self.attachments.valueHasMutated();
  };

  //helpers for checking relevant attachment states
  self.isApproved = function(attachment) {
    return attachment && attachment.state === self.APPROVED;
  };
  self.isRejected = function(attachment) {
    return attachment && attachment.state === self.REJECTED;
  };
  self.isNotNeeded = function(attachment) {
    return attachment && attachment.notNeeded === true;
  };

  function getUnwrappedAttachmentById(attachmentId) {
    return ko.utils.unwrapObservable(self.getAttachment(attachmentId));
  }

  // returns a function for use in computed
  // If all of the attachments are approved -> approved
  // If some of the attachments are rejected -> rejected
  // Else null
  self.attachmentsStatus = function(attachmentIds) {
    return function() {
      if (_.every(_.map(attachmentIds, getUnwrappedAttachmentById),
                  self.isApproved)) {
        return self.APPROVED;
      } else {
        return _.some(_.map(attachmentIds, getUnwrappedAttachmentById),
                      self.isRejected) ? self.REJECTED : null;
      }
    };
  };


  //
  // Attachment hierarchy
  //

  function findMatchingTag(tags, attachment) {
    return _.find(tags, function(tag) {
      return _.includes(attachment.tags, tag);
    }) || "default";
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

  function groupAttachmentsByTags(attachments) {
    return resolveTagGrouping(attachments, self.tagGroups());
  }

  //
  // Filter manipulation
  //

  // returns an observable
  var oldDummyFilters = {
        "hakemus": ko.observable(false),
        "rakentaminen": ko.observable(false),
        "ei-tarpeen": ko.observable(false),
        "iv": ko.observable(false),
        "kvv": ko.observable(false),
        "rakenne": ko.observable(false),
        "paapiirustukset": ko.observable(false)
  };

  self.disableAllFilters = function() {
    _.forEach(_.values(oldDummyFilters), function(filter) {
      filter(false);
    });
  };

    
  self.internedObservables = {};

  // keep track of filter toggles, since they are passed over to the UI, and
  // we need to keep using the same ones after tag updates
  function internFilterBoolean(key, def) {
    if (!self.internedObservables[key]) {
      var obs = self.freeObservables.pop();
      //console.log("interning ", key, " = ", obs);
      obs(def);
      //console.log(" - value is now ", obs());
      self.internedObservables[key] = obs;
    }
    return self.internedObservables[key];
  }

  self.filtersArray = ko.observableArray([]);

  self.filtersArrayDep = ko.computed( function() {
    var obsValues = _.map(self.freeObservables, function(f) { return f(); }); // cause a dependency on all free obs
    //console.log("filtersArrayDep being recomputed. obsvalues are ", obsValues);
    self.filtersArray(_.reverse(_.map(_.reduceRight(self.filters(), function (a, b) { return a.concat(b);}, []),
          function(filter, idx) {
           return {ltext: "filter." + filter.tag, tag: filter.tag, filter: internFilterBoolean(filter.tag, filter.default)};})))});

  function showAll() {
    return false;
    //var filterValues = _.mapValues(internedObservables, function(f) { return f(); });
    //return _(filterValues).omit("ei-tarpeen").values()
    //       .every(function(f) { return !f; });
  }

  function notNeededForModel(attachment) {
    return attachment.notNeeded;
  }

  function unwrapArrayValues(arr) {
    return _.mapValues(arr, ko.utils.unwrapObservable);
  }

  function unwrapValuePair(val, key) {
    var res = {};
    res[ko.utils.unwrapObservable(key)] = ko.utils.unwrapObservable(val);
    return res;
  }

  self.isAttachmentFiltered = function (att) {
    //console.log("isAttachmentFiltered: activeFilters is ", self.activeFilters());
    //console.log("Checkig if attachment with tags", att().tags, "matches filters", 
    //  _.map(self.activeFilters(), function (filter) { return filter.tag;}));
    return _.first(_.intersection(att().tags, _.map(self.activeFilters(), function (filter) { return filter.tag; })));
  };

  function applyFilters(attachments, active) {
    //console.log("applying new filters ", active, " to atachments");
    if (showAll()) {
      //console.log("showing all, bypassing filters");
      return attachments;
    }
    //console.log("actually running filters");
    return _.filter(attachments, self.isAttachmentFiltered);
  }

  //
  // Attachments hierarchy
  //

  // Return attachment ids grouped first by type-groups and then by type ids.
  self.getAttachmentsHierarchy = function() {
    var attachments = _.map(self.filteredAttachments(), ko.utils.unwrapObservable);
    return groupAttachmentsByTags(attachments);
  };

  self.attachmentsHierarchy = ko.pureComputed(self.getAttachmentsHierarchy);

  self.modelForAttachmentInfo = function(attachmentIds) {
    var attachments = _(attachmentIds)
          .map(self.getAttachment)
          .filter(_.identity)
          .value();
    return {
      approve:      self.approveAttachment,
      reject:       self.rejectAttachment,
      remove:       self.removeAttachment,
      setNotNeeded: self.setNotNeeded,
      isApproved:   self.isApproved,
      isRejected:   self.isRejected,
      isNotNeeded:  notNeededForModel,
      attachments:  attachments
    };
  };

  function modelForSubAccordion(subGroup) {
    _.forEach(_.values(oldDummyFilters), function(f) { f(); });
    var attachmentInfos = self.modelForAttachmentInfo(subGroup.attachmentIds);
    return {
      type: "sub",
      ltitle: subGroup.name, // TODO
      attachmentInfos: attachmentInfos,
      // all approved or some rejected
      status: ko.pureComputed(self.attachmentsStatus(subGroup.attachmentIds)),
      hasContent: ko.pureComputed(function() {
        return attachmentInfos && attachmentInfos.attachments &&
          attachmentInfos.attachments.length > 0;
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

  function someSubGroupsHaveContent(subGroups) {
    return ko.pureComputed(function() {
      return _.some(_.values(subGroups),
                    function(sg) { return sg.hasContent(); });
    });
  }

  function modelForMainAccordion(mainGroup) {
    var subGroups = _.mapValues(mainGroup.subGroups, groupToModel);
    return _.merge({
      type: "main",
      ltitle: mainGroup.name, // TODO
      status: subGroupsStatus(subGroups),
      hasContent: someSubGroupsHaveContent(subGroups)
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
    var group = util.getIn(self.attachmentsHierarchy(), groupPath) || "default";
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
        return modelForSubAccordion({
          lname: groupToAccordionName(groupPath),
          attachmentIds: getAttachmentsForGroup(groupPath)
        });
      })
    };
  }

  function attachmentTypeLayout(groupPath, tagGroups) {
    if (tagGroups.length) {
      return {
        lname: groupToAccordionName(groupPath),
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

  self.layout = ko.computed(function() {
    if (self.tagGroups().length) {
      return attachmentTypeLayout([], self.tagGroups());
    } else {
      return {};
    }
  });


  self.togglePreVerdictAccordions = function() {
    //toggleAccordions(0);
  };
  self.togglePostVerdictAccordions = function() {
    //toggleAccordions(1);
  };
  ko.options.deferUpdates = false;
};
