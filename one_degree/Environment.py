import numpy as np
import pandas as pd
import time

from Common import *

# The original environment is like "-------T" where T means terminal


def update_env(episode, totalStep, pos):
    env_list = ['-'] * (TOTAL_STATES - 1) + ['T']
    if pos == TOTAL_STATES - 1:
        text = "This is episode %d, step %d " % (episode, totalStep)
        print('\r{}'.format(text), end='')
        time.sleep(2)
        print("\r                                   ", end='')
    else:
        env_list[pos] = 'o'
        text = ''.join(env_list)
        print('\r{}'.format(text), end='')
        time.sleep(FRESH_TIME)


def build_q_table(n_states, actions):
    table = pd.DataFrame(
        np.zeros((n_states, len(actions))),     # q_table 全 0 初始
        columns=actions,    # columns 对应的是行为名称
    )
    return table


def choose_action(state, q_table):
    state_actions = q_table.iloc[state, :]
    if np.random.uniform() > EPSILON or state_actions.all() == 0:
        action = np.random.choice(ACTIONS)
    else:
        action = state_actions.idxmax()
    return action


def get_feedback(state, action):
    reward = 0
    if action == 'right':
        if state == TOTAL_STATES - 2:
            reward = 1
        next_state = state + 1
    else:
        if state == 0:
            next_state = state
        else:
            next_state = state - 1
    return next_state, reward
