/*
 * Wraps base autocomplete component, with internal handling of 'query'
 * and 'options', meaning the actual UI data filtering is done inside
 * this component (compared to LUPAPISTE.AutocompleteBaseModel, where filtering
 * should be implemented in adopting parent model).
 * Supports only single selection (takes 'selectedOption' as parameter).
 * By default doesn't allow null (undefined) as selected value, this can be changed
 * by setting 'nullable' parameter to true.
 * Required parameters:
 *   options (observalbe array): Array of selectable objects. Objects must have
 *                               'label' property, which is used as display name (and thus filtering).
 *                               This property can be overridden with 'optionsText' parameter.
 *   selectedOption (observable): Selected object. Selection is saved to this observable.
 * Optional parameters:
 *   optionsText (string): object's property, which is used as display name
 *   ... and all other parameters for base-autocomplete-model.js.
 */
LUPAPISTE.AutocompleteModel = function(params) {
  "use strict";
  var self = this;

  self.disable = params.disable || false;

  self.queryString = ko.observable("");

  self.results = ko.pureComputed(function() {
    return util.filterDataByQuery({data: params.options(),
                                   query: self.queryString(),
                                   selected: params.selectedOption(),
                                   label: params.optionsText});
  });

  var defaultParams = {tags: false};
  self.componentParams = _.defaults({query: self.queryString, options: self.results},
                                     params,
                                     defaultParams);

};
