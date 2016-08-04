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


  self.queryAll = function queryAll(attachmentId) {
    var fireQuery = function(commandName, responseJsonKey, dataSetter) {
      ajax.query(commandName, {"id": self.applicationId})
        .success(function(data) {
          dataSetter(data[responseJsonKey]);
        })
        .onError("error.unauthorized", notify.ajaxError)
        .call();
    };

    fireQuery("attachments", "attachments", self.setAttachments);
    fireQuery("attachments-tag-groups", "tag-groups", self.setTagGroups);
    fireQuery("attachments-filters", "attachments-filters", self.setFilters);
  };

  self.setAttachments = function(data) {
    self.attachments(_.map(data, createArrayModel));
  };

  self.setTagGroups = function(data) {
    data.push(["preVerdict"]);
    data.push(["postVerdict"]);
    self.tagGroups(_.map(data, createArrayModel));
  };

  self.setFilters = function(data) {
    self.filters(_.map(data, createArrayModel));
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

  var preVerdictStates = [
    "draft", "info", "answered", "open", "submitted", "complementNeeded", "sent"
  ];

  function isPreVerdict(attachment) {
    return _.includes(preVerdictStates, attachment.applicationState);
  }

  function isPostVerdict(attachment) {
    return !isPreVerdict(attachment);
  }

  function getVerdictGroup(attachment) {
    if (isPreVerdict(attachment)) {
      return "preVerdict";
    } else if (isPostVerdict(attachment)) {
      return "postVerdict";
    } else {
      return "default";
    }
  }

  function getAttachmentOperationId(attachment) {
    return attachment.op && attachment.op.id;
  }

  self.matchGroupsToAttchment = function(attachment) {
    // 1. get attachment tags
    // 2. ??
    // 3. match attachment tags against top level groups
    var isTopLevel = function(v) {
      if (typeof v[0] == "string" && v.length == 1)
        return "tag";
      else
        return "sub";
    };
    var groupingTagHierarchy = _.map(self.tagGroups(), ko.utils.unwrapObservable);
    //var mainGroups = _.filter(groupingTagHierarchy, isTopLevel);
    var groups = _.groupBy(groupingTagHierarchy, isTopLevel);
    groups.main = _.first(_.intersection(attachment.tags, groups.tag) || "default");
    return groups;
  };

    function getMainGroup(attachment) {
    // 1. get attachment tags
    // 2. ??
    // 3. match attachment tags against top level groups
    var isTopLevel = function(v) { return typeof v[0] == "string" && v.length == 1; };
    var groupingTagHierarchy = _.map(self.tagGroups(), ko.utils.unwrapObservable);
    var mainGroups = _.filter(groupingTagHierarchy, isTopLevel);
    var matchingMainGroup = _.intersection(attachment.tags, mainGroups);
    return _.first(matchingMainGroup) || "default"; // vai mainGroups[0]; ?
  }


  function getSubGroup(attachment) {
    // Dummy implementation for dummy data
    if (getMainGroup(attachment) === "osapuolet") {
      return "no-sub-group";
    } else if (getAttachmentOperationId(attachment)) {
      if (attachment.type["type-group"] === "paapiirustus") {
        return "paapiirustus";
      } else {
        return attachment.type["type-id"];
      }
    } else {
      return "no-sub-group";
    }
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

  self.filtersArray = ko.observableArray(
    _.map( ["hakemus", "rakentaminen", "paapiirustukset", "iv", "kvv",
            "rakenne", "ei-tarpeen"],
           function( s ) {
             return {ltext: "filter." + s,
                     filter: filters[s]};
           }));

  self.disableAllFilters = function() {
    _.forEach(_.values(filters), function(filter) {
      filter(false);
    });
  };

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

  function filteredAttachments(preOrPost, attachments) {
    var subFilters = [
      "iv",
      "kvv",
      "rakenne",
      "paapiirustukset"
    ];
    if (filters[preOrPost]() &&
        !_.some(subFilters, function(f) {
          return filters[f]();
        })) {
      return attachments;
    }
    return _.filter(attachments, function(attachment) {
      return _.some(subFilters, function(f) {
        return filters[f]() && filterFunctions[f](attachment);
      });
    });
  }

  function preVerdictAttachments(attachments) {
    return filteredAttachments("hakemus", attachments);
  }

  function postVerdictAttachments(attachments) {
    return filteredAttachments("rakentaminen", attachments);
  }

  function notNeededForModel(attachment) {
    return attachment.notNeeded;
  }

  function applyFilters(attachments) {
    var atts = _(attachments)
          .filter(function(attachment) {
            return filters["ei-tarpeen"]() || !self.isNotNeeded(attachment);
          }).value();
    if (showAll()) {
      return atts;
    }
    return _.concat(
      preVerdictAttachments(_.filter(atts, isPreVerdict)),
      postVerdictAttachments(_.filter(atts, isPostVerdict))
    );
  }

  //
  // Attachments hierarchy
  //

  // self.getAttachmentsHierarchy = function(attachments) { // undummy version
  //   attachments = attachments || _.map(self.attachments(), ko.util.unwrapObservable);
  //   var subgroup = function(attachmentsOfMainGroup) {
  //           var subGroups = _(attachmentsOfMainGroup)
  //             .groupBy(getSubGroup)
  //             .mapValues(function(attachments) {
  //               return _.map(attachments, "id");
  //             })
  //             .value();
  //           return subGroups["default"] || subGroups;
  //   };
  //   var unfiltered = function(attachmentsOfVerdictGroup) {
  //       return _(attachmentsOfVerdictGroup)
  //         .groupBy(getMainGroup)
  //         .mapValues(subgroup)
  //         .value();
  //   };
  //   // return _(applyFilters(attachments))
  //   //   .groupBy(getVerdictGroup)
  //   //   .mapValues(unfiltered)
  //   //   .value();
  //   return unfiltered;
  // };


  // Return attachment ids grouped first by type-groups and then by type ids.
  self.getAttachmentsHierarchy = function() {
    var attachments = _.map(self.attachments(), function (a) { return a(); });

    return _(applyFilters(attachments))
      .groupBy(getVerdictGroup)
      .mapValues(function(attachmentsOfVerdictGroup) {
        // console.log("attachmentsOfVerdictgroup", attachmentsOfVerdictGroup);
        return _(attachmentsOfVerdictGroup)
          .groupBy(getMainGroup)
          .mapValues(function(attachmentsOfMainGroup) {
            var subGroups = _(attachmentsOfMainGroup)
              .groupBy(getSubGroup)
              .mapValues(function(attachments) {
                // console.log("in inner getAttachments fn", attachments, attachments.length, attachments[0]["id"]);
                return _.map(attachments, "id");
              })
              .value();
            return subGroups["no-sub-group"] || subGroups;
          })
          .value();
      })
      .value();
  };
  self.attachmentsHierarchy = ko.pureComputed(self.getAttachmentsHierarchy);

  function getAttachmentById(attachmentId) {
    var attachment = self.getAttachment(attachmentId);
    if (attachment) {
      return attachment;
    } else {
      return null;
    }
  }


  self.modelForAttachmentInfo = function(attachmentIds) {
    var attachments = _(attachmentIds)
          .map(getAttachmentById)
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
    // console.log("modelForSubAccordion: attachmentIds", subGroup.attachmentIds);
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

  function getAttachmentsForGroup() {
    function getAttachments(group) {
      if (_.isPlainObject(group)) {
        return _.flatten(_.map(_.values(group), getAttachments));
      } else {
        return group;
      }
    }
    var args = _.toArray(arguments);
    var group = _.get(self.attachmentsHierarchy(), args) || "default";
    // console.log("getAttachmentsForGroup getting", args, group);
    return getAttachments(group);
  }

  function getDataForGroup() {
    var args = _.toArray(arguments);
    return ko.pureComputed(function() {
      return  _.merge(_.get(self.verdicts(), args));
    });
  }

  function getDataForAccordion() {
    var args = _.toArray(arguments);
    return {
      name: _.last(args),
      open: ko.observable(),
      data: ko.pureComputed(function() {
        return modelForSubAccordion({
          name: _.last(args),
          attachmentIds: _.spread(getAttachmentsForGroup)(args)
        });
      })
    };
  }

  function attachmentTypeLayout(verdictType) {
    return [
      getDataForAccordion(verdictType, "yleiset"),
      getDataForAccordion(verdictType, "osapuolet"),
      getDataForAccordion(verdictType, "rakennuspaikka"),
      {name: "op-id", // TODO generalize for any number of ops
       open: ko.observable(),
       data: getDataForGroup(verdictType, "op-id"),
       accordions: [
         getDataForAccordion(verdictType, "op-id", "paapiirustus"),
         getDataForAccordion(verdictType, "op-id", "kvv_suunnitelma"),
         getDataForAccordion(verdictType, "op-id", "iv_suunnitelma"),
         getDataForAccordion(verdictType, "op-id", "rakennesuunnitelma"),
         getDataForAccordion(verdictType, "op-id", "muu_suunnitelma")
       ]
      }
    ];
  }

  self.layout = [
    {
      name: "preVerdict",
      data: getDataForGroup("preVerdict"),
      accordions: attachmentTypeLayout("preVerdict")
    },
    {
      name: "postVerdict",
      data: getDataForGroup("postVerdict"),
      accordions: attachmentTypeLayout("postVerdict")
    },
    {
      name: "default",
      data: getDataForGroup("default"),
      accordions: attachmentTypeLayout("default")
    }

  ];


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
