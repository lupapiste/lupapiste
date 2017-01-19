LUPAPISTE.AttachmentGroupAutocompleteModel = function(params) {
  "use strict";
  var self = this;
  ko.utils.extend(self, new LUPAPISTE.ComponentBaseModel());

  var selectedGroup = params.group;
  self.disable = params.disable || ko.observable(false);

  var service = lupapisteApp.services.attachmentsService;

  function getGroupOptionsText(item) {
    if (_.get(item, "groupType") === "operation") {
      return _.filter([loc([item.name, "_group_label"]), item.description]).join(" - ");
    } else if (_.get(item, "groupType")) {
      return loc([item.groupType, "_group_label"]);
    }
  }

  var options = self.disposedPureComputed(function() {
    return _(service.groupTypes())
      .filter(function(group) {
        return _.size(_.get(selectedGroup(), "operations")) > 0 ? group.groupType === "operation" : true;
      })
      .map(function(group) {
        return _.set(group, "title", getGroupOptionsText(group));
      })
      .value();
  });

  function combineGroups(groups) {
    var groupType = _.get(_.last(groups), "groupType");
    var operations = groupType === "operation" ?
        _(groups).map(function(group) { return { id: group.id, name: group.name }; }).filter("id").value() :
        null;
    return groupType === "general" ? null : { groupType: groupType, operations: operations };
  }

  function divideGroup(group, groupTypes) {
    var groupType = _.get(group, "groupType");
    if (!groupType || _.isEmpty(groupTypes)) {
      return [];
    } else if (groupType === "operation") {
      return _.map(group.operations, function(op) { return _.find(groupTypes, ["id", op.id]); });
    } else {
      return [_.find(options(), ["groupType", group.groupType])];
    }
  }

  self.selected = self.disposedPureComputed({
    read: function() {
      return divideGroup(selectedGroup(), options());
    },
    write: function(selectedGroups) {
      selectedGroup(combineGroups(selectedGroups));
    }
  });

  self.selected.remove = function(pred) {
    self.selected(_.remove(self.selected(), function(item) {
      if (_.isFunction(pred)) {
        return !pred(item);
      } else {
        return _.has(pred, "id") ? pred.id !== item.id : pred.groupType !== item.groupType;
      }
    }));
  };

  self.selected.push = function(item) {
    self.selected(_.concat(self.selected(), item));
  };

  self.query = ko.observable("");

  self.data = self.disposedPureComputed(function() {
    return util.filterDataByQuery({ data: options(),
                                    query: self.query(),
                                    selected: self.selected(),
                                    label: "title" });
  });

};
