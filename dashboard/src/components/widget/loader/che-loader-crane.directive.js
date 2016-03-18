/*
 * Copyright (c) 2015-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 */
'use strict';

/**
 * Defines a directive for animating iteration process
 * @author Oleksii Kurinnyi
 */
export class CheLoaderCrane {

  /**
   * Default constructor that is using resource
   * @ngInject for Dependency injection
   */
  constructor($timeout, $window) {
    this.$timeout = $timeout;
    this.$window = $window;
    this.restrict = 'E';
    this.replace = true;
    this.templateUrl = 'components/widget/loader/che-loader-crane.html';

    // scope values
    this.scope = {
      step: '@cheStep',
      allSteps: '=cheAllSteps',
      excludeSteps: '=cheExcludeSteps',
      switchOnIteration: '=?cheSwitchOnIteration',
      inProgress: '=cheInProgress'
    };
  }

  link($scope, element) {
    let craneEl = element.find('.che-loader-crane'),
      cargoEl = element.find('#che-loader-crane-load'),
      oldSteps = [],
      newStep,
      animationStopping = false,
      animationRunning = false;

    $scope.$watch(() => {
      return $scope.step;
    }, (newVal) => {
      newVal = parseInt(newVal, 10);

      // try to stop animation on last step
      if (newVal === $scope.allSteps.length - 1) {
        animationStopping = true;

        if (!$scope.switchOnIteration) {
          // stop animation immediately if it shouldn't wait until next iteration
          setNoAnimation();
        }
      }

      // skip steps excluded
      if ($scope.excludeSteps.indexOf(newVal) !== -1) {
        return;
      }

      newStep = newVal;

      // go to next step
      // if animation hasn't run yet or it shouldn't wait until next iteration
      if (!animationRunning || !$scope.switchOnIteration) {
        setAnimation();
        setCurrentStep();
      }

      if (oldSteps.indexOf(newVal) === -1) {
        oldSteps.push(newVal);
      }
    });

    let destroyResizeEvent;
    $scope.$watch(() => {
      return $scope.inProgress;
    }, (inProgress) => {
      console.log('inProgress: ', inProgress);
      // destroy event
      if (!inProgress && typeof destroyResizeEvent === 'function') {
        destroyResizeEvent();
        return;
      }

      // initial resize
      setCraneSize();

      destroyResizeEvent = angular.element(this.$window).bind('resize', (event) => {
        console.log('>>> window resize stopped with event: ', event);

        setCraneSize();

        // re-init all animations
        // let animatedElements = element.find('.che-loader-animation'),
        //   foundAnimations = [];
        // for (let i=0; i<animatedElements.length; i++) {
        //   let animationName = removeAnimation(animatedElements[i]);
        //   foundAnimations.push([animatedElements[i],animationName]);
        // }
        //
        // this.$timeout(() => {
        //   for (let i=0; i<foundAnimations.length; i++) {
        //     let pair = foundAnimations[i];
        //     applyAnimation(pair[0], pair[1]);
        //   }
        // },0);
      });
    });

    if (!!$scope.switchOnIteration) {
      element.find('.che-loader-animation.trolley-block').bind('animationstart', () => {
        animationRunning = true;
      });
      element.find('.che-loader-animation.trolley-block').bind('animationiteration', () => {
        if (oldSteps.length){
          setCurrentStep();
        }
        else if (animationStopping) {
          setNoAnimation();
        }
      });
    }

    let setCraneSize = () => {
        console.log('> trying to set crane size');

        // check if parent container has scrollbar
        let contentPage = angular.element('#create-project-content-page')[0],
          scrollEl = element.find('.che-loader-crane-scale-wrapper'),
          scale = 1,
          scaleIncrement = 0.1,
          height = craneEl.height(),
          width = craneEl.width();
        scrollEl.css('transform', 'scale3d('+scale+','+scale+','+scale+')');
        scrollEl.css('height', height * scale);
        scrollEl.css('width', width * scale);

        let bodyEl = angular.element(document).find('body')[0];

        while (((bodyEl && bodyEl.scrollHeight - bodyEl.offsetHeight > 10) || contentPage.scrollHeight > contentPage.offsetHeight) && scale > 0.7) {
          scale = scale - scaleIncrement;
          console.log('> page has scroll bar, apply scale ', scale);
          scrollEl.css('transform', 'scale3d('+scale+','+scale+','+scale+')');
          scrollEl.css('height', height * scale);
          scrollEl.css('width', width * scale);
        }

        // parent container still has scroll
        if (((bodyEl && bodyEl.scrollHeight - bodyEl.offsetHeight > 10) || contentPage.scrollHeight > contentPage.offsetHeight) && scale <= 0.7) {
          console.log('hide parent element');
          element.parent().css('display','none');
        }
        else {
          console.log('show parent element');
          element.parent().css('display','block');
        }
      },
      // removeAnimation = (el) => {
      //   el = angular.element(el);
      //   let animationName = el.css('animation-name');
      //   el.css('animation-name', 'none');
      //   console.log('remove animation named ', animationName, ' from ', el);
      //   return animationName;
      // },
      // applyAnimation = (el, animationName) => {
      //   console.log('apply animation named ', animationName, ' to ', el);
      //   el = angular.element(el);
      //   el.css('animation-name', animationName);
      // },
      setAnimation = () => {
        craneEl.removeClass('che-loader-no-animation');
      },
      setNoAnimation = () => {
        animationRunning = false;
        craneEl.addClass('che-loader-no-animation');
      },
      setCurrentStep = () => {
        for (let i = 0; i < oldSteps.length; i++) {
          craneEl.removeClass('step-' + oldSteps[i]);
          cargoEl.removeClass('layer-' + oldSteps[i]);
        }
        oldSteps.length = 0;

        // avoid next layer blinking
        let currentLayer = element.find('.layers-in-box').find('.layer-'+newStep);
        currentLayer.css('visibility','hidden');
        this.$timeout(() => {
          currentLayer.removeAttr('style');
        },500);

        craneEl.addClass('step-' + newStep);
        cargoEl.addClass('layer-' + newStep);
      };
  }
}
