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

  //
  // Attachments
  //

  function createArrayModel(attachment) {
    return ko.observable(attachment);
  }

  self.attachments = ko.observableArray([]);
  self.tagGroups = ko.observableArray([]);

  self.filters = ko.observableArray([]);
  self.activeFilters = ko.pureComputed(function() {return self.filters();});

  self.filteredAttachments = ko.pureComputed(
    function() {
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

  function getUnwrappedAttachment(attachmentId) {
    var attachment = self.getAttachment(attachmentId);
    if (attachment) {
      return attachment();
    } else {
      return null;
    }
  }

  // returns a function for use in computed
  // If all of the attachments are approved -> approved
  // If some of the attachments are rejected -> rejected
  // Else null
  self.attachmentsStatus = function(attachmentIds) {
    return function() {
      if (_.every(_.map(attachmentIds, getUnwrappedAttachment),
                  self.isApproved)) {
        return self.APPROVED;
      } else {
        return _.some(_.map(attachmentIds, getUnwrappedAttachment),
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

  var preVerdictStates = [
    "draft", "info", "answered", "open", "submitted", "complementNeeded", "sent"
  ];

  function isPreVerdict(attachment) {
    return _.includes(preVerdictStates, attachment.applicationState);
  }

  function isPostVerdict(attachment) {
    return !isPreVerdict(attachment);
  }

  //
  // Filter manipulation
  //

  // returns an observable
  var filters = {
        "hakemus": ko.observable(false),
        "rakentaminen": ko.observable(false),
        "ei-tarpeen": ko.observable(false),
        "iv": ko.observable(false),
        "kvv": ko.observable(false),
        "rakenne": ko.observable(false),
        "paapiirustukset": ko.observable(false)
  };

  self.disableAllFilters = function() {
    _.forEach(_.values(filters), function(filter) {
      filter(false);
    });
  };

  self.filtersArray = ko.observableArray(
    _.map( ["hakemus", "rakentaminen", "paapiirustukset", "iv", "kvv",
            "rakenne", "ei-tarpeen"],
           function( s ) {
             return {ltext: "filter." + s,
                     filter: filters[s]};
           }));

  function isTypeId(typeId) {
    return function(attachment) {
      return attachment.type["type-id"] === typeId;
    };
  }

  function isTypeGroup(typeGroup) {
    return function(attachment) {
      return attachment.type["type-group"] === typeGroup;
    };
  }

  var filterFunctions = {
    "hakemus": isPreVerdict,
    "rakentaminen": isPostVerdict,
    "ei-tarpeen": self.isNotNeeded,
    "iv": isTypeId("iv_suunnitelma"),
    "kvv": isTypeId("kvv_suunnitelma"),
    "rakenne": isTypeId("rakennesuunnitelma"),
    "paapiirustukset": isTypeGroup("paapiirustus")
  };

  function showAll() {
    var filterValues = _.mapValues(filters, function(f) { return f(); });
    return _(filterValues).omit("ei-tarpeen").values()
           .every(function(f) { return !f; });
  }

  function notNeededForModel(attachment) {
    return attachment.notNeeded;
  }

  function unwrapValuePair(val, key) {
    var res = {};
    res[ko.utils.unwrapObservable(key)] = ko.utils.unwrapObservable(val);
    return res;
  }

  self.isAttachmentFiltered = function (att) {
    return _.first(_.intersection(att().tags, _.map(self.activeFilters()[0], function (filter) { return filter.tag; })));
  };

  function applyFilters(attachments) {
    if (showAll()) {
      return attachments;
    }
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
    _.forEach(_.values(filters), function(f) { f(); });
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


  //
  // Automatically open relevant accordions when filters are toggled.
  //

  function attachmentsToAmounts(val) {
    if (_.isPlainObject(val)) {
      return _.mapValues(val, attachmentsToAmounts);
    } else if (_.isArray(val)) {
      return val.length;
    } else {
      return _.clone(val);
    }
  }

  function mergeAttachmentAmounts(newVal, oldVal) {
    if (_.isPlainObject(newVal)) {
      return _.mapValues(newVal, function(value, key) {
        return mergeAttachmentAmounts(value, (oldVal && oldVal[key]) || null);
      });
    } else if (_.isNumber(newVal)) {
      return newVal - (oldVal || 0);
    } else {
      return 0;
    }
  }

  function objectToPaths(obj) {
    if (!_.isPlainObject(obj)) {
      return [obj];
    } else {
      return _(obj).mapValues(objectToPaths).toPairs().value();
    }
  }

  function getTogglesInPaths(paths) {
    function getToggles(paths, objectOrArray) {
      if (_.isArray(objectOrArray)) {
        return _.flatten(_.map(paths, function(path) {
          var subPath = _.find(objectOrArray, function(x) {
            return x.name === _.first(path);
          });
          return getToggles(_.tail(path), subPath);
        }));
      } else if (_.isPlainObject(objectOrArray)) {
        return _.concat(objectOrArray.open ? [objectOrArray.open] : [],
                        getToggles(_.first(paths), objectOrArray.accordions));
      } else {
        return [];
      }
    }
    return getToggles(paths, self.layout);
  }

  // Track changes in filter values and visible attachments in the hierarchy
  var previousAttachmentsHierarchy = self.attachmentsHierarchy();
  self.attachmentsHierarchy.subscribe(function(oldValue) {
    previousAttachmentsHierarchy = oldValue;
  }, self, "beforeChange");

  self.previousFilterValues = _.mapValues(filters, function() { return false; });
  var filterValues = ko.pureComputed(function() {
    return _.mapValues(filters, function(f) { return f(); });
  });
  filterValues.subscribe(function(oldValue) {
    self.previousFilterValues = oldValue || self.previousFilterValues;
  }, self, "beforeChange");

  // Open relevant accordions on filter toggle
  ko.computed(function() {
    if (_.some(_.keys(filterValues()), function(k) {
      return k !== "hakemus" && k !== "rakentaminen" &&
        filterValues.peek()[k]  && !self.previousFilterValues[k];
    })) {
      var diff =  _.mergeWith(attachmentsToAmounts(self.attachmentsHierarchy.peek()),
                              attachmentsToAmounts(previousAttachmentsHierarchy),
                              mergeAttachmentAmounts);
      var diffPaths = objectToPaths(diff);
      var toggles = getTogglesInPaths(diffPaths, self.layout);
      _.forEach(toggles, function(toggle) {
        toggle(true);
      });
    }
  }).extend({rateLimit: {timeout: 0}});

  function getAccordionToggles(preOrPost) {
    function getAllToggles(objectOrArray) {
      if (_.isArray(objectOrArray)) {
        return _.flatten(_.map(objectOrArray, getAllToggles));
      } else if (_.isObject(objectOrArray)) {
        return _.concat(objectOrArray.open ? [objectOrArray.open] : [],
                        getAllToggles(objectOrArray.accordions));
      } else {
        return [];
      }
    }
    return getAllToggles(self.layout[preOrPost].accordions);
  }

  function toggleAccordions(preOrPost) {
    var toggles = getAccordionToggles(preOrPost);
    if (_.every(toggles, function(t) { return t(); })) {
      _.forEach(toggles, function(t) { t(false); });
    } else {
      _.forEach(toggles, function(t) { t(true); });
    }
  }

  self.togglePreVerdictAccordions = function() {
    toggleAccordions(0);
  };
  self.togglePostVerdictAccordions = function() {
    toggleAccordions(1);
  };
  ko.options.deferUpdates = false;
};
